# Lab OpenTelemetry no OpenShift

Este repositorio entrega um laboratorio de observabilidade para demonstrar como aplicacoes Java podem enviar traces e expor metricas no OpenShift usando OpenTelemetry, Tempo, Prometheus e Grafana.

Este README e a documentacao principal para preparar o ambiente. Ele cobre os produtos usados, a arquitetura e os passos de instalacao/configuracao no OpenShift.

As configuracoes dentro de cada aplicacao ficam em documentos separados:

- [Quarkus - configuracao da aplicacao](quarkus-opentelemetry/README.md)
- [Spring Boot - configuracao da aplicacao](spring-opentelemetry/README.md)

## Objetivo

Ao final do lab, você tera:

- uma aplicacao Quarkus instrumentada com OpenTelemetry e Micrometer;
- uma aplicacao Spring Boot instrumentada com Micrometer Tracing, OpenTelemetry OTLP exporter e Actuator;
- um OpenTelemetry Collector gerenciado pelo Red Hat build of OpenTelemetry;
- um TempoStack armazenando traces em object storage S3 via OpenShift Data Foundation/NooBaa;
- metricas coletadas pelo OpenShift User Workload Monitoring/Prometheus;
- dashboards no Grafana exibindo traces, erros, spans, latencia, requests, CPU e memoria.

## Produtos e componentes usados

| Componente | Uso no lab | Observacao |
|---|---|---|
| Red Hat OpenShift | Plataforma de execucao | Namespace, workloads, routes, builds e monitoring |
| Red Hat build of OpenTelemetry | Operator e Collector | Recebe traces OTLP das aplicacoes |
| Tempo Operator | TempoStack | Armazena e consulta traces |
| OpenShift Data Foundation / NooBaa | Object storage S3 | Backend de armazenamento do Tempo |
| OpenShift Monitoring / Prometheus | Coleta de metricas | User Workload Monitoring + ServiceMonitor |
| Grafana Operator | Grafana e dashboards | No lab vem de `community-operators` |
| Quarkus | Aplicacao de exemplo | Demonstra `quarkus-opentelemetry` e `/q/metrics` |
| Spring Boot | Aplicacao de exemplo | Demonstra Actuator, Micrometer e OTLP exporter |

Nota de suporte: os componentes Red Hat do fluxo sao OpenShift, Red Hat build of OpenTelemetry, Tempo Operator, OpenShift Monitoring e ODF quando houver subscricao. O Grafana Operator usado neste lab vem do catalogo comunitario. As bibliotecas das aplicacoes seguem o ecossistema de cada framework e devem ser validadas conforme a matriz de suporte do cliente.

## Referencias oficiais Red Hat

Use estas paginas como referencia oficial para validar instalacao, suporte, parametros e comportamento dos componentes Red Hat usados neste lab:

| Tema | Documentacao oficial |
|---|---|
| Red Hat build of OpenTelemetry | [Installing the Red Hat build of OpenTelemetry](https://docs.redhat.com/en/documentation/openshift_container_platform/latest/html/observability/otel-installing) |
| Tempo Operator / TempoStack | [Installing the distributed tracing platform Tempo](https://docs.redhat.com/en/documentation/openshift_container_platform/latest/html/observability/distr-tracing-tempo-installing) |
| User Workload Monitoring / Prometheus | [Configuring user workload monitoring](https://docs.redhat.com/en/documentation/openshift_container_platform/latest/html/monitoring/configuring-user-workload-monitoring) |
| OpenShift Data Foundation / ObjectBucketClaim | [Object Bucket Claim](https://docs.redhat.com/en/documentation/red_hat_openshift_data_foundation/latest/html/managing_hybrid_and_multicloud_resources/object-bucket-claim) |
| Builds com BuildConfig | [Builds using BuildConfig](https://docs.redhat.com/en/documentation/openshift_container_platform/latest/html/builds_using_buildconfig/index) |

Os manifests deste repositorio automatizam uma forma de montar o ambiente para demonstracao. Em ambientes de cliente, confirme versoes, canais de Operator, sizing, armazenamento, retencao, TLS, RBAC e politicas de seguranca com a documentacao oficial da Red Hat.

## Arquitetura

```text
Aplicacao Quarkus
  -> traces OTLP gRPC :4317
  -> Red Hat build of OpenTelemetry Collector
  -> TempoStack
  -> ODF/NooBaa object storage

Aplicacao Spring Boot
  -> traces OTLP HTTP :4318/v1/traces
  -> Red Hat build of OpenTelemetry Collector
  -> TempoStack
  -> ODF/NooBaa object storage

Aplicacoes
  -> metricas Prometheus
  -> ServiceMonitor
  -> OpenShift User Workload Monitoring / Prometheus

Grafana
  -> datasource Tempo
  -> datasource Prometheus/Thanos
  -> dashboards Quarkus e Spring
```

## Estrutura do repositorio

```text
.
├── README.md
├── quarkus-opentelemetry
│   ├── README.md
│   ├── openshift
│   ├── scripts
│   ├── src
│   └── pom.xml
└── spring-opentelemetry
    ├── README.md
    ├── src
    └── pom.xml
```

Os manifests OpenShift do lab ficam em:

```text
quarkus-opentelemetry/openshift
```

Mesmo a aplicacao Spring usa manifests nessa pasta, para manter a stack OpenShift do lab em um unico local.

## Pre-requisitos

Antes de iniciar, valide:

- acesso a um cluster OpenShift;
- permissao para instalar Operators;
- permissao para criar recursos em `openshift-operators`, `openshift-storage`, `openshift-monitoring` e no namespace do lab;
- CLI `oc` autenticado no cluster;
- acesso aos catalogs `redhat-operators`, `community-operators` e `openshift-marketplace`;
- capacidade de storage para o ODF/NooBaa;
- permissao para habilitar User Workload Monitoring.

Login:

```bash
oc login --token=<TOKEN> --server=<API_SERVER>
```

## Namespace do lab

O namespace usado neste laboratorio e:

```text
opentelemetry
```

Manifesto:

```text
quarkus-opentelemetry/openshift/01-namespace.yaml
```

Aplicar:

```bash
oc apply -f quarkus-opentelemetry/openshift/01-namespace.yaml
oc project opentelemetry
```

## 1. Instalar Operators

### Red Hat build of OpenTelemetry, Tempo Operator e Grafana Operator

Manifesto:

```text
quarkus-opentelemetry/openshift/00-operators.yaml
```

Ele cria as subscriptions:

- `opentelemetry-product`, a partir de `redhat-operators`;
- `tempo-product`, a partir de `redhat-operators`;
- `grafana-operator`, a partir de `community-operators`.

Referencias oficiais:

- [Installing the Red Hat build of OpenTelemetry](https://docs.redhat.com/en/documentation/openshift_container_platform/latest/html/observability/otel-installing)
- [Installing the distributed tracing platform Tempo](https://docs.redhat.com/en/documentation/openshift_container_platform/latest/html/observability/distr-tracing-tempo-installing)

Aplicar:

```bash
oc apply -f quarkus-opentelemetry/openshift/00-operators.yaml
```

Validar:

```bash
oc get subscriptions -n openshift-operators
oc get csv -n openshift-operators
```

### OpenShift Data Foundation

Manifesto:

```text
quarkus-opentelemetry/openshift/00-odf-operator.yaml
```

Aplicar:

```bash
oc apply -f quarkus-opentelemetry/openshift/00-odf-operator.yaml
```

Validar:

```bash
oc get csv -n openshift-storage
```

## 2. Validar CRDs necessarios

Depois dos Operators, valide se as APIs ficaram disponiveis:

```bash
oc api-resources --cached=false | grep -Ei 'opentelemetry|tempo|grafana|servicemonitor|objectbucket|noobaa'
```

APIs esperadas:

- `opentelemetrycollectors`
- `tempostacks`
- `grafanas`
- `grafanadatasources`
- `grafanadashboards`
- `servicemonitors`
- `objectbucketclaims`
- `noobaas`

## 3. Criar object storage para o Tempo

O Tempo precisa de object storage para armazenar traces. Neste lab usamos OpenShift Data Foundation/NooBaa.

Referencia oficial:

- [Object Bucket Claim](https://docs.redhat.com/en/documentation/red_hat_openshift_data_foundation/latest/html/managing_hybrid_and_multicloud_resources/object-bucket-claim)

Manifesto:

```text
quarkus-opentelemetry/openshift/04-odf-object-storage.yaml
```

Aplicar:

```bash
oc apply -f quarkus-opentelemetry/openshift/04-odf-object-storage.yaml
```

Validar NooBaa:

```bash
oc -n openshift-storage get noobaa
```

Validar ObjectBucketClaim:

```bash
oc -n opentelemetry get obc tempo-bucket
```

O `ObjectBucketClaim` cria:

- `ConfigMap tempo-bucket`;
- `Secret tempo-bucket`;
- bucket S3 para o Tempo.

O TempoStack espera uma Secret com nomes de chaves especificos. Crie a Secret `tempo-object-storage` a partir do OBC:

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

## 4. Criar TempoStack

Manifesto:

```text
quarkus-opentelemetry/openshift/05-tempostack.yaml
```

Esse manifesto cria:

- `ConfigMap noobaa-service-ca`;
- `TempoStack sample`;
- `Service tempo-otlp`, usado como endpoint OTLP estavel para o Collector.

Referencia oficial:

- [Installing the distributed tracing platform Tempo](https://docs.redhat.com/en/documentation/openshift_container_platform/latest/html/observability/distr-tracing-tempo-installing)

Aplicar:

```bash
oc apply -f quarkus-opentelemetry/openshift/05-tempostack.yaml
```

Validar:

```bash
oc -n opentelemetry get tempostack
oc -n opentelemetry get pods | grep tempo
oc -n opentelemetry get svc | grep tempo
```

Endpoint de consulta usado pelo Grafana:

```text
http://tempo-sample-query-frontend.opentelemetry.svc:3200
```

Endpoint de ingestao usado pelo Collector:

```text
tempo-otlp.opentelemetry.svc:4317
```

## 5. Criar OpenTelemetry Collector

Manifesto:

```text
quarkus-opentelemetry/openshift/04-otel-collector.yaml
```

O Collector criado pelo Red Hat build of OpenTelemetry:

- recebe OTLP gRPC em `4317`;
- recebe OTLP HTTP em `4318`;
- aplica `memory_limiter` e `batch`;
- exporta traces para o Tempo via `tempo-otlp.opentelemetry.svc:4317`.

Referencia oficial:

- [Installing the Red Hat build of OpenTelemetry](https://docs.redhat.com/en/documentation/openshift_container_platform/latest/html/observability/otel-installing)

Aplicar:

```bash
oc apply -f quarkus-opentelemetry/openshift/04-otel-collector.yaml
```

Validar:

```bash
oc -n opentelemetry get opentelemetrycollector
oc -n opentelemetry get pods | grep otel
oc -n opentelemetry logs deploy/otel-collector --tail=50
```

## 6. Habilitar metricas de workloads de usuario

O OpenShift Monitoring precisa estar habilitado para coletar metricas de aplicacoes em namespaces de usuario.

Referencia oficial:

- [Configuring user workload monitoring](https://docs.redhat.com/en/documentation/openshift_container_platform/latest/html/monitoring/configuring-user-workload-monitoring)

Manifesto:

```text
quarkus-opentelemetry/openshift/07-user-workload-monitoring.yaml
```

Aplicar:

```bash
oc apply -f quarkus-opentelemetry/openshift/07-user-workload-monitoring.yaml
```

Validar:

```bash
oc -n openshift-user-workload-monitoring get pods
```

Os ServiceMonitors das aplicacoes serao criados nos passos de deploy das aplicacoes.

## 7. Criar Grafana, datasources e permissoes

### RBAC para o Grafana consultar Prometheus/Thanos

Manifesto:

```text
quarkus-opentelemetry/openshift/10-grafana-prometheus-rbac.yaml
```

Aplicar:

```bash
oc apply -f quarkus-opentelemetry/openshift/10-grafana-prometheus-rbac.yaml
```

Esse manifesto cria permissoes para a ServiceAccount do Grafana consultar metricas do OpenShift Monitoring.

### Grafana e datasource Tempo

Manifesto:

```text
quarkus-opentelemetry/openshift/06-grafana-operator.yaml
```

Aplicar:

```bash
oc apply -f quarkus-opentelemetry/openshift/06-grafana-operator.yaml
```

Validar:

```bash
oc -n opentelemetry get grafana
oc -n opentelemetry get grafanadatasource
oc -n opentelemetry get route grafana-route
```

Datasource Tempo:

```text
name: Tempo
uid: tempo
url: http://tempo-sample-query-frontend.opentelemetry.svc:3200
```

### Datasource Prometheus

No lab, o datasource Prometheus aponta para o Thanos Querier do OpenShift Monitoring:

```text
https://thanos-querier.openshift-monitoring.svc:9091
```

Gere um token para a ServiceAccount do Grafana:

```bash
PROMETHEUS_TOKEN=$(oc create token grafana-sa -n opentelemetry --duration=8760h)
```

Crie a Secret usada como referencia:

```bash
oc -n opentelemetry create secret generic grafana-prometheus-token \
  --from-literal=bearerToken="Bearer ${PROMETHEUS_TOKEN}" \
  --dry-run=client -o yaml | oc apply -f -
```

Atualize/crie o datasource pela API do Grafana:

```bash
GRAFANA_URL=https://$(oc -n opentelemetry get route grafana-route -o jsonpath='{.spec.host}')

curl -fsS -u admin:admin -H 'Content-Type: application/json' -X POST \
  "$GRAFANA_URL/api/datasources" \
  -d '{"name":"prometheus","uid":"prometheus","type":"prometheus","access":"proxy","url":"https://thanos-querier.openshift-monitoring.svc:9091","isDefault":false,"jsonData":{"httpMethod":"POST","httpHeaderName1":"Authorization","tlsSkipVerify":true,"timeInterval":"15s"},"secureJsonData":{"httpHeaderValue1":"Bearer '"${PROMETHEUS_TOKEN}"'"}}'
```

Se o datasource ja existir, use `PUT`:

```bash
curl -fsS -u admin:admin -H 'Content-Type: application/json' -X PUT \
  "$GRAFANA_URL/api/datasources/uid/prometheus" \
  -d '{"name":"prometheus","uid":"prometheus","type":"prometheus","access":"proxy","url":"https://thanos-querier.openshift-monitoring.svc:9091","isDefault":false,"jsonData":{"httpMethod":"POST","httpHeaderName1":"Authorization","tlsSkipVerify":true,"timeInterval":"15s"},"secureJsonData":{"httpHeaderValue1":"Bearer '"${PROMETHEUS_TOKEN}"'"}}'
```

## 8. Aplicar dashboards

Dashboards:

```text
quarkus-opentelemetry/openshift/09-grafana-dashboard.yaml
quarkus-opentelemetry/openshift/12-spring-grafana-dashboard.yaml
```

Aplicar:

```bash
oc apply -f quarkus-opentelemetry/openshift/09-grafana-dashboard.yaml
oc apply -f quarkus-opentelemetry/openshift/12-spring-grafana-dashboard.yaml
```

Validar:

```bash
oc -n opentelemetry get grafanadashboard
```

Importante: os dashboards sao gerenciados pelo Grafana Operator. Edicoes feitas diretamente na UI do Grafana podem ser sobrescritas pelo Operator. Para mudancas permanentes, altere os manifests YAML.

## 9. Deploy da aplicacao Quarkus

Guia especifico da aplicacao:

```text
quarkus-opentelemetry/README.md
```

Aplicar objetos e build:

```bash
oc apply -f quarkus-opentelemetry/openshift/08-buildconfig.yaml
oc apply -f quarkus-opentelemetry/openshift/02-app.yaml
oc apply -f quarkus-opentelemetry/openshift/03-servicemonitor.yaml
oc -n opentelemetry start-build quarkus-otel-observability --from-dir=quarkus-opentelemetry --follow
oc -n opentelemetry rollout status deploy/quarkus-otel-observability
```

Validar rota:

```bash
oc -n opentelemetry get route quarkus-otel-observability
```

## 10. Deploy da aplicacao Spring Boot

Guia especifico da aplicacao:

```text
spring-opentelemetry/README.md
```

Aplicar objetos e build:

```bash
oc apply -f quarkus-opentelemetry/openshift/11-spring-app.yaml
oc -n opentelemetry start-build spring-otel-observability --from-dir=spring-opentelemetry --follow
oc -n opentelemetry rollout status deploy/spring-otel-observability
```

Validar rota:

```bash
oc -n opentelemetry get route spring-otel-observability
```

## 11. Validacoes finais

### Pods

```bash
oc -n opentelemetry get pods
```

### Routes

```bash
oc -n opentelemetry get route
```

### Collector

```bash
oc -n opentelemetry logs deploy/otel-collector --tail=50
```

### Tempo

```bash
oc -n opentelemetry get tempostack
oc -n opentelemetry logs deploy/tempo-sample-query-frontend --tail=50
```

### ServiceMonitors

```bash
oc -n opentelemetry get servicemonitor
```

### Metricas Quarkus

```bash
QUARKUS_URL=https://$(oc -n opentelemetry get route quarkus-otel-observability -o jsonpath='{.spec.host}')
curl "$QUARKUS_URL/q/metrics"
```

### Metricas Spring

```bash
SPRING_URL=https://$(oc -n opentelemetry get route spring-otel-observability -o jsonpath='{.spec.host}')
curl "$SPRING_URL/actuator/prometheus"
```

### Consulta TraceQL no Grafana/Tempo

Quarkus:

```text
{ resource.service.name = "quarkus-otel-observability" }
```

Spring:

```text
{ resource.service.name = "spring-otel-observability" }
```

## 12. Gerar massa de dados

### Quarkus

```bash
QUARKUS_URL=https://$(oc -n opentelemetry get route quarkus-otel-observability -o jsonpath='{.spec.host}')

for i in {1..30}; do
  curl -s "$QUARKUS_URL/observability/hello/user-$i" > /dev/null
  curl -s "$QUARKUS_URL/observability/inventory" > /dev/null
  curl -s "$QUARKUS_URL/observability/checkout/customer-$i?items=4" > /dev/null
  curl -s "$QUARKUS_URL/observability/slow?ms=900" > /dev/null
  curl -s -o /dev/null -w "error request: HTTP %{http_code}\n" "$QUARKUS_URL/observability/error"
  sleep 1
done
```

### Spring Boot

```bash
SPRING_URL=https://$(oc -n opentelemetry get route spring-otel-observability -o jsonpath='{.spec.host}')

for i in {1..30}; do
  curl -s "$SPRING_URL/spring/hello/user-$i" > /dev/null
  curl -s "$SPRING_URL/spring/order/customer-$i?items=4" > /dev/null
  if [ $((i % 4)) -eq 0 ]; then
    curl -s "$SPRING_URL/spring/error" > /dev/null
  fi
  sleep 1
done
```

## 13. Troubleshooting

### Grafana nao mostra traces

Verifique se ha dados recentes no range de tempo do dashboard. Os dashboards podem estar em `Last 30 minutes` ou `Last 6 hours`.

Valide o query-frontend:

```bash
oc -n opentelemetry get pods | grep tempo-sample-query-frontend
oc -n opentelemetry describe pod -l app.kubernetes.io/component=query-frontend
```

Se houver `OOMKilled`, aumente os recursos do TempoStack em:

```text
quarkus-opentelemetry/openshift/05-tempostack.yaml
```

### Grafana nao mostra metricas

Valide:

```bash
oc -n opentelemetry get servicemonitor
oc -n openshift-user-workload-monitoring get pods
```

Confira se o datasource Prometheus possui token Bearer valido.

## Proximos passos para o cliente

Depois que o ambiente estiver preparado, use os documentos especificos das aplicacoes para entender o que precisa ser configurado no codigo:

- [Quarkus - configuracao da aplicacao](quarkus-opentelemetry/README.md)
- [Spring Boot - configuracao da aplicacao](spring-opentelemetry/README.md)

Esses documentos explicam as dependencias, propriedades, variaveis de ambiente, endpoints de metricas e criacao de spans customizados em cada stack.

### Aplicacao nao envia traces

Valide se a variavel `OTEL_EXPORTER_OTLP_ENDPOINT` aponta para o Collector:

```bash
oc -n opentelemetry set env deploy/quarkus-otel-observability --list | grep OTEL
oc -n opentelemetry set env deploy/spring-otel-observability --list | grep OTEL
```

Valide logs do Collector:

```bash
oc -n opentelemetry logs deploy/otel-collector --tail=100
```

## Referencias deste repositorio

- [Quarkus - configuracao da aplicacao](quarkus-opentelemetry/README.md)
- [Spring Boot - configuracao da aplicacao](spring-opentelemetry/README.md)

## Aviso final

Este repositorio e uma recomendacao tecnica para demonstrar uma arquitetura de observabilidade com OpenTelemetry no OpenShift. Ele nao substitui a documentacao oficial da Red Hat. Para implantacoes em ambiente produtivo, siga sempre as documentacoes oficiais, a matriz de suporte vigente, as politicas internas do cliente e o dimensionamento recomendado para a versao do OpenShift e dos Operators instalados.
