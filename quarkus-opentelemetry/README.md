# Configuracao da aplicacao Quarkus

Este documento orienta como a aplicacao Quarkus deste repositorio foi configurada para enviar traces e expor metricas no ambiente OpenShift preparado pelo [README principal](../README.md).

Use este guia quando o objetivo for entender ou replicar a configuracao dentro de uma aplicacao Quarkus.

## Referencias oficiais

Principais referencias para validar a abordagem em ambientes Red Hat:

| Tema | Documentacao oficial |
|---|---|
| Red Hat build of Quarkus | [Red Hat build of Quarkus documentation](https://docs.redhat.com/en/documentation/red_hat_build_of_quarkus/latest) |
| Red Hat build of OpenTelemetry e Tempo no OpenShift | [Distributed tracing documentation](https://docs.redhat.com/en/documentation/openshift_container_platform/latest/html/distributed_tracing/index) |
| User Workload Monitoring / Prometheus | [Monitoring documentation](https://docs.redhat.com/en/documentation/openshift_container_platform/latest/html/monitoring/index) |

O codigo deste lab demonstra a instrumentacao da aplicacao. Em ambiente produtivo, valide a versao do Red Hat build of Quarkus, extensoes habilitadas, padrao de build e versoes de dependencias com a documentacao oficial e a matriz de suporte vigente.

## Visao geral

```text
Quarkus
  -> quarkus-opentelemetry
  -> OTLP gRPC
  -> Red Hat build of OpenTelemetry Collector

Quarkus
  -> quarkus-micrometer-registry-prometheus
  -> /q/metrics
  -> OpenShift Monitoring / Prometheus
```

## Dependencias Maven

Arquivo:

```text
pom.xml
```

Dependencias relevantes:

```xml
<dependency>
  <groupId>io.quarkus</groupId>
  <artifactId>quarkus-rest</artifactId>
</dependency>
<dependency>
  <groupId>io.quarkus</groupId>
  <artifactId>quarkus-rest-jackson</artifactId>
</dependency>
<dependency>
  <groupId>io.quarkus</groupId>
  <artifactId>quarkus-opentelemetry</artifactId>
</dependency>
<dependency>
  <groupId>io.quarkus</groupId>
  <artifactId>quarkus-micrometer-registry-prometheus</artifactId>
</dependency>
<dependency>
  <groupId>io.quarkus</groupId>
  <artifactId>quarkus-smallrye-health</artifactId>
</dependency>
```

Funcoes:

| Dependencia | Funcao |
|---|---|
| `quarkus-rest` | Endpoints REST |
| `quarkus-rest-jackson` | Respostas JSON |
| `quarkus-opentelemetry` | Instrumentacao e exportacao OTLP |
| `quarkus-micrometer-registry-prometheus` | Metricas Prometheus |
| `quarkus-smallrye-health` | Health checks para probes |

## Propriedades da aplicacao

Arquivo:

```text
src/main/resources/application.properties
```

Configuracao usada:

```properties
quarkus.application.name=quarkus-otel-observability
quarkus.http.host=0.0.0.0
quarkus.http.port=8080

quarkus.otel.enabled=true
quarkus.otel.exporter.otlp.endpoint=${OTEL_EXPORTER_OTLP_ENDPOINT:http://localhost:4317}
quarkus.otel.resource.attributes=service.namespace=${OTEL_SERVICE_NAMESPACE:opentelemetry},deployment.environment=${DEPLOYMENT_ENVIRONMENT:local}

quarkus.micrometer.export.prometheus.path=/q/metrics
quarkus.micrometer.binder.http-server.enabled=true
quarkus.micrometer.binder.jvm=true
quarkus.micrometer.binder.system=true

quarkus.log.category."com.example.observability".level=INFO
```

### Nome da aplicacao

```properties
quarkus.application.name=quarkus-otel-observability
```

Define o nome do servico. Esse valor aparece no Tempo/Grafana como `service.name`.

### Porta HTTP

```properties
quarkus.http.host=0.0.0.0
quarkus.http.port=8080
```

Permite que a aplicacao escute dentro do container na porta `8080`.

### Traces

```properties
quarkus.otel.enabled=true
```

Ativa OpenTelemetry no Quarkus.

Referencia oficial relacionada:

- [Distributed tracing documentation](https://docs.redhat.com/en/documentation/openshift_container_platform/latest/html/distributed_tracing/index)

```properties
quarkus.otel.exporter.otlp.endpoint=${OTEL_EXPORTER_OTLP_ENDPOINT:http://localhost:4317}
```

Define o endpoint OTLP. No OpenShift, a variavel `OTEL_EXPORTER_OTLP_ENDPOINT` aponta para:

```text
http://otel-collector.opentelemetry.svc:4317
```

O Quarkus envia traces via OTLP gRPC.

```properties
quarkus.otel.resource.attributes=service.namespace=${OTEL_SERVICE_NAMESPACE:opentelemetry},deployment.environment=${DEPLOYMENT_ENVIRONMENT:local}
```

Adiciona atributos de recurso aos traces:

- `service.namespace`
- `deployment.environment`

Esses atributos ajudam em filtros no Grafana/Tempo.

### Metricas

```properties
quarkus.micrometer.export.prometheus.path=/q/metrics
```

Expoe metricas Prometheus em:

```text
/q/metrics
```

```properties
quarkus.micrometer.binder.http-server.enabled=true
```

Habilita metricas HTTP, como contagem de requests, status e latencia por rota.

```properties
quarkus.micrometer.binder.jvm=true
quarkus.micrometer.binder.system=true
```

Habilita metricas de JVM e sistema.

## Spans customizados

O Quarkus ja cria spans automaticos para requests HTTP. Para explicar o fluxo interno da aplicacao, o codigo tambem cria spans de negocio com `@WithSpan`.

Exemplo:

```java
@WithSpan("checkout.create-order")
public CheckoutResult checkout(String customer, int items) {
    Span.current().setAttribute("demo.customer", customer);
    Span.current().setAttribute("demo.items", items);
    ...
}
```

`@WithSpan` cria uma etapa visivel no trace.

`Span.current().setAttribute(...)` adiciona informacoes ao span atual.

Spans usados no lab:

- `checkout.create-order`
- `checkout.calculate-total`
- `inventory.reserve`
- `inventory.lookup`
- `payment.authorize`
- `demo.hello`
- `demo.inventory`
- `demo.slow-operation`

Exemplo de trace esperado:

```text
GET /observability/checkout/{customer}
  -> checkout.create-order
  -> inventory.reserve
  -> checkout.calculate-total
  -> payment.authorize
```

## Erros nos traces

A rota `/observability/error` registra uma excecao no span atual e marca o trace como erro.

Isso permite demonstrar no Grafana:

- traces com erro;
- status HTTP 500;
- span associado ao erro;
- contexto da falha.

## Endpoints da aplicacao

| Endpoint | Uso |
|---|---|
| `/observability/help` | Lista as rotas de demo |
| `/observability/hello/{name}` | Request simples |
| `/observability/inventory` | Consulta de inventario |
| `/observability/checkout/{customer}?items=3` | Fluxo de negocio com multiplos spans |
| `/observability/slow?ms=800` | Simulacao de latencia |
| `/observability/error` | Simulacao de erro |
| `/q/metrics` | Metricas Prometheus |
| `/q/health/ready` | Readiness probe |
| `/q/health/live` | Liveness probe |

## Configuracao no Deployment

Manifesto:

```text
openshift/02-app.yaml
```

Variaveis relevantes:

```yaml
- name: OTEL_SERVICE_NAME
  value: quarkus-otel-observability
- name: OTEL_SERVICE_NAMESPACE
  value: opentelemetry
- name: DEPLOYMENT_ENVIRONMENT
  value: openshift
- name: OTEL_EXPORTER_OTLP_ENDPOINT
  value: http://otel-collector.opentelemetry.svc:4317
```

Essas variaveis fazem a aplicacao enviar traces ao Collector criado pelo Red Hat build of OpenTelemetry.

## ServiceMonitor

Manifesto:

```text
openshift/03-servicemonitor.yaml
```

O ServiceMonitor instrui o Prometheus do OpenShift a coletar:

```text
/q/metrics
```

Referencia oficial relacionada:

- [Monitoring documentation](https://docs.redhat.com/en/documentation/openshift_container_platform/latest/html/monitoring/index)

## Build e deploy

A partir da raiz do repositorio:

```bash
oc apply -f quarkus-opentelemetry/openshift/08-buildconfig.yaml
oc apply -f quarkus-opentelemetry/openshift/02-app.yaml
oc apply -f quarkus-opentelemetry/openshift/03-servicemonitor.yaml
oc -n opentelemetry start-build quarkus-otel-observability --from-dir=quarkus-opentelemetry --follow
oc -n opentelemetry rollout status deploy/quarkus-otel-observability
```

## Validacao

Obter URL:

```bash
QUARKUS_URL=https://$(oc -n opentelemetry get route quarkus-otel-observability -o jsonpath='{.spec.host}')
echo "$QUARKUS_URL"
```

Testar endpoints:

```bash
curl "$QUARKUS_URL/observability/hello/ana"
curl "$QUARKUS_URL/observability/checkout/ana?items=4"
curl "$QUARKUS_URL/observability/slow?ms=900"
curl -i "$QUARKUS_URL/observability/error"
```

Ver metricas:

```bash
curl "$QUARKUS_URL/q/metrics"
```

Consulta TraceQL no Grafana:

```text
{ resource.service.name = "quarkus-otel-observability" }
```

Consulta para spans de negocio:

```text
{ resource.service.name = "quarkus-otel-observability" && name =~ ".*(checkout|inventory|payment).*" }
```

## Pontos para adaptação

- Ajustar `quarkus.application.name` para o nome real do servico.
- Ajustar `OTEL_EXPORTER_OTLP_ENDPOINT` para o Collector do ambiente.
- Definir `service.namespace` e `deployment.environment` conforme seu padrao.
- Rever amostragem, retencao e cardinalidade de atributos antes de producao.
- Evitar atributos com dados sensiveis ou alta cardinalidade, como documentos, tokens ou e-mails completos.

## Nota para uso

Este documento descreve a configuracao aplicada neste lab Quarkus. Para producao, use-o como referencia inicial e confirme a implementacao final com as documentacoes oficiais da Red Hat, matriz de suporte, padroes internos e requisitos de seguranca/compliance.
