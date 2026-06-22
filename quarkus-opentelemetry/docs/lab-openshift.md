# Lab OpenTelemetry no OpenShift

Este lab mostra, de forma apresentavel, como o OpenTelemetry atua em uma aplicacao Quarkus:

```text
Aplicacao Quarkus instrumentada
  -> envia traces via OTLP
OpenTelemetry Collector, criado pelo RHBO
  -> recebe, processa e encaminha traces
Grafana Tempo
  -> armazena traces em bucket S3 do OpenShift Data Foundation
Grafana
  -> exibe traces e metricas no mesmo dashboard

Aplicacao Quarkus
  -> expoe metricas em /q/metrics
Prometheus Operator / OpenShift User Workload Monitoring
  -> coleta metricas via ServiceMonitor
Grafana
  -> exibe CPU, memoria, pods, restarts, volume por rota e latencia media
```

Namespace do lab:

```text
opentelemetry
```

## Operators

Use componentes Red Hat para a parte de plataforma:

- **Red Hat build of OpenTelemetry**: cria o `OpenTelemetryCollector`.
- **Tempo Operator**: cria o `TempoStack`.
- **OpenShift Data Foundation**: cria NooBaa/Multicloud Object Gateway e fornece bucket S3 para o Tempo.
- **Grafana Operator**: cria a instancia do Grafana e o datasource Tempo.
- **Prometheus Operator do OpenShift**: coleta metricas da aplicacao com `ServiceMonitor` e User Workload Monitoring.

Nao usamos MinIO nem Jaeger neste lab. O Prometheus usado e o gerenciado pelo OpenShift, nao um Prometheus manual separado.

Instalacao por manifesto quando necessario:

```bash
oc apply -f openshift/00-odf-operator.yaml
oc apply -f openshift/00-operators.yaml
```

Valide os CRDs:

```bash
oc api-resources | grep -Ei 'opentelemetry|tempo|grafana|noobaa|objectbucket'
oc api-resources | grep -Ei 'servicemonitor|prometheus'
```

## Caminho automatizado

Depois que o `oc` estiver disponivel e logado no cluster:

```bash
chmod +x scripts/deploy-lab.sh
./scripts/deploy-lab.sh
```

O script valida CRDs, cria o namespace, cria NooBaa e `ObjectBucketClaim`, gera o Secret S3 do Tempo, habilita User Workload Monitoring, cria `ServiceMonitor`, cria TempoStack, Grafana, Collector, builda a app dentro do OpenShift e gera chamadas de exemplo.

## Passo a passo manual

### 1. Namespace

```bash
oc apply -f openshift/01-namespace.yaml
oc project opentelemetry
```

### 2. Object storage Red Hat com ODF/NooBaa

```bash
oc apply -f openshift/04-odf-object-storage.yaml
```

Valide:

```bash
oc -n openshift-storage get noobaa
oc -n opentelemetry get obc tempo-bucket
```

Quando o OBC estiver `Bound`, crie o Secret no formato esperado pelo Tempo:

```bash
BUCKET_NAME=$(oc -n opentelemetry get configmap tempo-bucket -o jsonpath='{.data.BUCKET_NAME}')
BUCKET_HOST=$(oc -n opentelemetry get configmap tempo-bucket -o jsonpath='{.data.BUCKET_HOST}')
BUCKET_PORT=$(oc -n opentelemetry get configmap tempo-bucket -o jsonpath='{.data.BUCKET_PORT}')
ACCESS_KEY=$(oc -n opentelemetry get secret tempo-bucket -o jsonpath='{.data.AWS_ACCESS_KEY_ID}' | base64 -d)
SECRET_KEY=$(oc -n opentelemetry get secret tempo-bucket -o jsonpath='{.data.AWS_SECRET_ACCESS_KEY}' | base64 -d)

oc -n opentelemetry create secret generic tempo-object-storage \
  --from-literal=endpoint="https://${BUCKET_HOST}:${BUCKET_PORT}" \
  --from-literal=bucket="${BUCKET_NAME}" \
  --from-literal=access_key_id="${ACCESS_KEY}" \
  --from-literal=access_key_secret="${SECRET_KEY}" \
  --dry-run=client -o yaml | oc apply -f -
```

### 3. TempoStack

```bash
oc apply -f openshift/05-tempostack.yaml
oc -n opentelemetry get tempostack
oc -n opentelemetry get pods | grep -i tempo
```

O Collector envia traces para:

```text
tempo-otlp.opentelemetry.svc:4317
```

### 4. Prometheus Operator / User Workload Monitoring

Habilite o monitoramento de workloads de usuario do OpenShift:

```bash
oc apply -f openshift/07-user-workload-monitoring.yaml
```

Crie permissao para o Grafana consultar as metricas do OpenShift e gere o token usado pelo datasource Prometheus:

```bash
oc apply -f openshift/10-grafana-prometheus-rbac.yaml

PROMETHEUS_TOKEN=$(oc create token grafana-sa -n opentelemetry --duration=8760h)
oc -n opentelemetry create secret generic grafana-prometheus-token \
  --from-literal=bearerToken="Bearer ${PROMETHEUS_TOKEN}" \
  --dry-run=client -o yaml | oc apply -f -
```

O `ServiceMonitor` diz ao Prometheus Operator para coletar as metricas da aplicacao em `/q/metrics`:

```bash
oc apply -f openshift/03-servicemonitor.yaml
oc -n opentelemetry get servicemonitor
```

Os datasources Prometheus do Grafana sao criados pela API do Grafana, nao por `GrafanaDatasource`, porque neste cluster o Operator reconcilia `secureJsonData.httpHeaderValue1` vazio e remove o token bearer necessario para consultar o Thanos Querier.

### 5. Grafana

```bash
oc apply -f openshift/06-grafana-operator.yaml
oc apply -f openshift/09-grafana-dashboard.yaml
oc -n opentelemetry get grafana
oc -n opentelemetry get grafanadatasource
oc -n opentelemetry get grafanadashboard
oc -n opentelemetry get route
```

Se o datasource Prometheus aparecer no Grafana mas retornar `Authentication to data source failed`, atualize o `secureJsonData` diretamente pela API do Grafana. O token nao deve ser salvo no repositorio:

```bash
GRAFANA_URL=https://$(oc -n opentelemetry get route grafana-route -o jsonpath='{.spec.host}')
PROMETHEUS_TOKEN=$(oc create token grafana-sa -n opentelemetry --duration=8760h)

curl -fsS -u admin:admin -H 'Content-Type: application/json' -X PUT \
  "$GRAFANA_URL/api/datasources/uid/prometheus" \
  -d '{"name":"prometheus","uid":"prometheus","type":"prometheus","access":"proxy","url":"https://thanos-querier.openshift-monitoring.svc:9091","isDefault":false,"jsonData":{"httpMethod":"POST","httpHeaderName1":"Authorization","tlsSkipVerify":true,"timeInterval":"15s"},"secureJsonData":{"httpHeaderValue1":"Bearer '"${PROMETHEUS_TOKEN}"'"}}'
```

Datasource:

```text
Tempo
prometheus
Prometheus
```

O datasource `Prometheus` com P maiusculo e mantido como alias para dashboards/imports que resolvem datasource por nome antigo. O datasource principal usado pelos paineis novos e `prometheus`.

Dashboard:

```text
OpenTelemetry Lab - Traces e metricas
```

### 6. OpenTelemetry Collector

```bash
oc apply -f openshift/04-otel-collector.yaml
oc -n opentelemetry get opentelemetrycollector
oc -n opentelemetry logs deploy/otel-collector
```

### 7. Build e deploy da aplicacao

```bash
oc apply -f openshift/08-buildconfig.yaml
oc -n opentelemetry start-build quarkus-otel-observability --from-dir=. --follow
oc apply -f openshift/02-app.yaml
oc -n opentelemetry rollout status deploy/quarkus-otel-observability
```

URL da app:

```bash
APP_URL=https://$(oc -n opentelemetry get route quarkus-otel-observability -o jsonpath='{.spec.host}')
echo $APP_URL
```

## Como explicar OpenTelemetry na aplicacao

Trace simples:

```bash
curl "$APP_URL/observability/hello/lucas"
```

Explique:

```text
A requisicao HTTP cria um span automatico.
O metodo anotado com @WithSpan cria um span adicional chamado demo.hello.
O SDK exporta o trace via OTLP para o Collector.
```

Fluxo de negocio:

```bash
curl "$APP_URL/observability/checkout/ana?items=4"
```

Mostre no Grafana:

```text
HTTP GET /observability/checkout/{customer}
  checkout.create-order
    inventory.reserve
    checkout.calculate-total
    payment.authorize
```

Latencia:

```bash
curl "$APP_URL/observability/slow?ms=1200"
```

Erro:

```bash
curl -i "$APP_URL/observability/error"
```

Gerar volume:

```bash
for i in $(seq 1 20); do
  curl -s "$APP_URL/observability/checkout/user-$i?items=$((1 + i % 5))" > /dev/null
done
```

No Grafana:

1. Abra a rota do Grafana.
2. Abra o dashboard **OpenTelemetry Lab - Traces e metricas**.
3. Use os paineis de traces para todas as requests, checkout, latencia e erro.
4. Use os paineis Prometheus para CPU, memoria, pods, restarts, volume por rota e latencia media por rota.
5. Explique a diferenca: Tempo mostra uma request especifica; Prometheus mostra comportamento agregado ao longo do tempo.
6. Para investigar um trace especifico, va em **Explore**, selecione **Tempo** e procure pelo service name `quarkus-otel-observability`.

## Leituras no Grafana

As visualizacoes ficam divididas assim:

Tempo:

- todas as requests da aplicacao;
- requests de checkout;
- traces com erro no geral;
- requests lentas;
- rotas de demo baseadas nos spans da aplicacao.

Prometheus Operator / OpenShift User Workload Monitoring:

- CPU atual e historico da aplicacao;
- memoria atual e historico da aplicacao;
- pods prontos;
- restarts;
- volume de requests por rota e status;
- latencia media por rota.

Frase de fechamento:

```text
OpenTelemetry nao e a tela final. Ele padroniza a instrumentacao e o transporte da telemetria; Tempo e Prometheus armazenam sinais diferentes, e o Grafana junta esses sinais em uma historia unica.
```
