# Instrumentacao da aplicacao Quarkus

Esta aplicacao foi preparada para demonstrar dois sinais de observabilidade:

- **traces**, enviados via OpenTelemetry/OTLP para o Collector;
- **metricas**, expostas em formato Prometheus em `/q/metrics`.

## Dependencias adicionadas

No `pom.xml`, a parte essencial para observabilidade e:

```xml
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

O papel de cada uma:

- `quarkus-opentelemetry`: ativa instrumentacao OpenTelemetry no Quarkus e exporta traces via OTLP.
- `quarkus-micrometer-registry-prometheus`: expoe metricas em formato Prometheus.
- `quarkus-smallrye-health`: cria endpoints de health check usados pelas probes do OpenShift.

## Configuracao para traces

Em `src/main/resources/application.properties`:

```properties
quarkus.application.name=quarkus-otel-observability

quarkus.otel.enabled=true
quarkus.otel.exporter.otlp.endpoint=${OTEL_EXPORTER_OTLP_ENDPOINT:http://localhost:4317}
quarkus.otel.resource.attributes=service.namespace=${OTEL_SERVICE_NAMESPACE:opentelemetry},deployment.environment=${DEPLOYMENT_ENVIRONMENT:local}
```

O que isso faz:

- `quarkus.application.name` vira o `service.name` visto no Tempo/Grafana.
- `quarkus.otel.enabled=true` liga o OpenTelemetry.
- `quarkus.otel.exporter.otlp.endpoint` define para onde os traces sao enviados.
- `quarkus.otel.resource.attributes` adiciona contexto ao trace, como namespace e ambiente.

No OpenShift, o endpoint OTLP vem do `Deployment`:

```yaml
env:
  - name: OTEL_SERVICE_NAME
    value: quarkus-otel-observability
  - name: OTEL_SERVICE_NAMESPACE
    value: opentelemetry
  - name: DEPLOYMENT_ENVIRONMENT
    value: openshift
  - name: OTEL_EXPORTER_OTLP_ENDPOINT
    value: http://otel-collector.opentelemetry.svc:4317
```

Com isso, a aplicacao envia traces para o **Red Hat build of OpenTelemetry Collector**, e nao diretamente para o Tempo.

## Traces automaticos

So de adicionar `quarkus-opentelemetry`, o Quarkus ja instrumenta automaticamente as requisicoes HTTP.

Exemplo:

```bash
curl "$APP_URL/observability/hello/lucas"
```

Isso gera um span automatico de servidor HTTP para a rota chamada.

## Spans manuais com `@WithSpan`

Para a demonstracao ficar mais didatica, o codigo adiciona spans de negocio com `@WithSpan`.

Exemplo em `ObservabilityResource`:

```java
@GET
@Path("/hello/{name}")
@WithSpan("demo.hello")
public Map<String, Object> hello(@PathParam("name") String name) {
    Span.current().setAttribute("demo.name", name);
    return Map.of("message", "Ola, " + name);
}
```

Exemplos em servicos internos:

```java
@WithSpan("checkout.create-order")
public CheckoutResult checkout(String customer, int items) {
    Span.current().setAttribute("demo.customer", customer);
    Span.current().setAttribute("demo.items", items);
    ...
}
```

```java
@WithSpan("inventory.reserve")
public List<String> reserve(int quantity) {
    Span.current().setAttribute("demo.inventory.reserved", quantity);
    ...
}
```

```java
@WithSpan("payment.authorize")
public String authorize(String customer, BigDecimal amount) {
    Span.current().setAttribute("demo.payment.amount", amount.doubleValue());
    ...
}
```

Na pratica, isso permite mostrar no Tempo uma arvore parecida com:

```text
HTTP GET /observability/checkout/{customer}
  checkout.create-order
    inventory.reserve
    checkout.calculate-total
    payment.authorize
```

## Atributos nos spans

O codigo usa `Span.current().setAttribute(...)` para enriquecer os traces:

```java
Span.current().setAttribute("demo.customer", customer);
Span.current().setAttribute("demo.items", items);
Span.current().setAttribute("demo.order.total", total.doubleValue());
```

Esses atributos ajudam a explicar que OpenTelemetry nao mostra apenas infraestrutura. Ele tambem pode carregar informacoes relevantes do fluxo de negocio.

## Traces de erro

A rota `/observability/error` registra uma excecao no span atual e marca o span como erro:

```java
RuntimeException exception = new IllegalStateException("Erro intencional para demonstrar observabilidade");
Span.current().recordException(exception);
Span.current().setStatus(StatusCode.ERROR, exception.getMessage());
throw new WebApplicationException(exception.getMessage(), 500);
```

Isso faz o trace aparecer como erro no Tempo/Grafana.

## Configuracao para metricas

Em `src/main/resources/application.properties`:

```properties
quarkus.micrometer.export.prometheus.path=/q/metrics
quarkus.micrometer.binder.http-server.enabled=true
quarkus.micrometer.binder.jvm=true
quarkus.micrometer.binder.system=true
```

O que isso faz:

- `/q/metrics`: endpoint Prometheus da aplicacao.
- `http-server`: metricas HTTP por rota, metodo, status e tempo.
- `jvm`: metricas da JVM, como memoria, GC, threads e classes.
- `system`: metricas de sistema/processo, como CPU.

Exemplos de metricas expostas:

```text
http_server_requests_seconds_count
http_server_requests_seconds_sum
jvm_memory_used_bytes
jvm_threads_live_threads
process_cpu_usage
system_cpu_usage
```

## Metricas automaticas por rota

Nao foi necessario criar `Counter` ou `Timer` manual no codigo.

O Micrometer cria metricas HTTP automaticamente para as rotas JAX-RS, por exemplo:

```text
http_server_requests_seconds_count{
  method="GET",
  status="200",
  uri="/observability/checkout/{customer}"
}
```

Essas metricas sao coletadas pelo Prometheus Operator atraves do `ServiceMonitor`:

```yaml
apiVersion: monitoring.coreos.com/v1
kind: ServiceMonitor
metadata:
  name: quarkus-otel-observability
spec:
  selector:
    matchLabels:
      app: quarkus-otel-observability
  endpoints:
    - port: http
      path: /q/metrics
      interval: 15s
```

## Resumo para apresentar

Use esta explicacao curta:

```text
Para traces, a aplicacao recebeu a extensao quarkus-opentelemetry, configuracao OTLP e spans de negocio com @WithSpan.

Para metricas, a aplicacao recebeu Micrometer Prometheus, expondo /q/metrics. O Prometheus Operator descobre esse endpoint via ServiceMonitor e o Grafana consulta essas metricas.

OpenTelemetry explica o caminho de uma request. Prometheus mostra o comportamento agregado da aplicacao ao longo do tempo.
```
