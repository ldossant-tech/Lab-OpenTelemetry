# Configuracao da aplicacao Spring Boot

Este documento orienta como a aplicacao Spring Boot deste repositorio foi configurada para enviar traces e expor metricas no ambiente OpenShift preparado pelo [README principal](../README.md).

Use este guia quando o objetivo for entender ou replicar a configuracao dentro de uma aplicacao Spring Boot.

## Referencias oficiais

Principais referencias Red Hat relacionadas a este lab:

| Tema | Documentacao oficial |
|---|---|
| Red Hat build of OpenTelemetry e Tempo no OpenShift | [Distributed tracing documentation](https://docs.redhat.com/en/documentation/openshift_container_platform/latest/html/distributed_tracing/index) |
| User Workload Monitoring / Prometheus | [Monitoring documentation](https://docs.redhat.com/en/documentation/openshift_container_platform/latest/html/monitoring/index) |
| Builds com BuildConfig | [Builds using BuildConfig](https://docs.redhat.com/en/documentation/openshift_container_platform/latest/html/builds_using_buildconfig/index) |

Neste exemplo, a aplicacao usa Spring Boot, Actuator, Micrometer Tracing e exporter OTLP. Os componentes Red Hat suportados no fluxo sao o OpenShift, Red Hat build of OpenTelemetry, OpenShift Monitoring/Prometheus e a base de execucao usada no cluster. As bibliotecas Spring/Micrometer devem ser validadas conforme a matriz de suporte, padroes internos e stack Java adotada.

## Visao geral

```text
Spring Boot
  -> Micrometer Tracing
  -> bridge OpenTelemetry
  -> OTLP HTTP exporter
  -> Red Hat build of OpenTelemetry Collector

Spring Boot
  -> Actuator + Micrometer
  -> /actuator/prometheus
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
  <groupId>org.springframework.boot</groupId>
  <artifactId>spring-boot-starter-actuator</artifactId>
</dependency>
<dependency>
  <groupId>org.springframework.boot</groupId>
  <artifactId>spring-boot-starter-web</artifactId>
</dependency>
<dependency>
  <groupId>io.micrometer</groupId>
  <artifactId>micrometer-registry-prometheus</artifactId>
</dependency>
<dependency>
  <groupId>io.micrometer</groupId>
  <artifactId>micrometer-tracing-bridge-otel</artifactId>
</dependency>
<dependency>
  <groupId>io.opentelemetry</groupId>
  <artifactId>opentelemetry-exporter-otlp</artifactId>
</dependency>
```

Funcoes:

| Dependencia | Funcao |
|---|---|
| `spring-boot-starter-web` | Endpoints HTTP |
| `spring-boot-starter-actuator` | Health, info e endpoint Prometheus |
| `micrometer-registry-prometheus` | Exportacao de metricas em formato Prometheus |
| `micrometer-tracing-bridge-otel` | Ponte entre Micrometer Tracing e OpenTelemetry |
| `opentelemetry-exporter-otlp` | Exportacao OTLP para o Collector |

## Propriedades da aplicacao

Arquivo:

```text
src/main/resources/application.properties
```

Configuracao usada:

```properties
spring.application.name=spring-otel-observability
server.port=8080

management.tracing.enabled=true
management.tracing.sampling.probability=1.0
management.otlp.tracing.endpoint=${OTEL_EXPORTER_OTLP_ENDPOINT:http://localhost:4318/v1/traces}

management.opentelemetry.resource-attributes.service.namespace=${OTEL_SERVICE_NAMESPACE:opentelemetry}
management.opentelemetry.resource-attributes.deployment.environment=${DEPLOYMENT_ENVIRONMENT:local}

management.endpoints.web.exposure.include=health,info,prometheus
management.endpoint.health.probes.enabled=true
management.prometheus.metrics.export.enabled=true
management.metrics.tags.application=${spring.application.name}

logging.level.com.example.springobservability=INFO
```

### Nome da aplicacao

```properties
spring.application.name=spring-otel-observability
```

Define o nome do servico. Esse valor aparece nos traces como `service.name` e tambem e usado como tag `application` nas metricas.

### Traces

```properties
management.tracing.enabled=true
```

Ativa o tracing no Spring Boot/Micrometer. Esta propriedade nao e OpenTelemetry pura; ela liga o mecanismo de tracing do Spring.

Como a aplicacao possui `micrometer-tracing-bridge-otel` e `opentelemetry-exporter-otlp`, os traces sao enviados usando OpenTelemetry/OTLP.

No OpenShift, esses traces sao recebidos pelo Collector gerenciado pelo Red Hat build of OpenTelemetry:

- [Distributed tracing documentation](https://docs.redhat.com/en/documentation/openshift_container_platform/latest/html/distributed_tracing/index)

```properties
management.tracing.sampling.probability=1.0
```

Define amostragem de 100% dos traces. Para laboratorio, isso facilita a demonstracao. Em producao, ajuste conforme suas politicas.

```properties
management.otlp.tracing.endpoint=${OTEL_EXPORTER_OTLP_ENDPOINT:http://localhost:4318/v1/traces}
```

Define o endpoint OTLP HTTP. No OpenShift, a variavel `OTEL_EXPORTER_OTLP_ENDPOINT` aponta para:

```text
http://otel-collector.opentelemetry.svc:4318/v1/traces
```

### Atributos OpenTelemetry

```properties
management.opentelemetry.resource-attributes.service.namespace=${OTEL_SERVICE_NAMESPACE:opentelemetry}
management.opentelemetry.resource-attributes.deployment.environment=${DEPLOYMENT_ENVIRONMENT:local}
```

Adiciona atributos aos traces:

- `service.namespace`
- `deployment.environment`

Esses atributos ajudam em filtros no Grafana/Tempo.

### Metricas

```properties
management.endpoints.web.exposure.include=health,info,prometheus
```

Expoe os endpoints Actuator usados no lab.

```properties
management.endpoint.health.probes.enabled=true
```

Habilita endpoints de readiness e liveness:

```text
/actuator/health/readiness
/actuator/health/liveness
```

```properties
management.prometheus.metrics.export.enabled=true
```

Habilita a exportacao de metricas Prometheus.

Endpoint:

```text
/actuator/prometheus
```

```properties
management.metrics.tags.application=${spring.application.name}
```

Adiciona a tag `application=spring-otel-observability` nas metricas.

## Spans customizados

No Spring Boot, este lab cria spans manualmente com `io.micrometer.tracing.Tracer`.

Exemplo:

```java
Span span = tracer.nextSpan().name("spring.payment.authorize").start();
try (Tracer.SpanInScope ignored = tracer.withSpan(span)) {
    span.tag("demo.payment.customer", customer);
    span.tag("demo.payment.amount", total.toPlainString());
    simulateLatency(120, 280);
} finally {
    span.end();
}
```

O que acontece:

1. `tracer.nextSpan()` cria um novo span.
2. `.name("spring.payment.authorize")` define o nome visto no Tempo/Grafana.
3. `.start()` inicia a medicao de tempo.
4. `tracer.withSpan(span)` torna o span atual dentro do bloco.
5. `span.tag(...)` adiciona atributos.
6. `span.end()` encerra o span.

Spans usados no lab:

- `spring.order.create`
- `spring.order.calculate-total`
- `spring.payment.authorize`

Exemplo de trace esperado:

```text
GET /spring/order/{customer}
  -> spring.order.create
  -> spring.order.calculate-total
  -> spring.payment.authorize
```

## Endpoints da aplicacao

| Endpoint | Uso |
|---|---|
| `/spring/hello/{name}` | Request simples |
| `/spring/order/{customer}?items=3` | Fluxo de negocio com spans customizados |
| `/spring/error` | Simulacao de erro |
| `/actuator/prometheus` | Metricas Prometheus |
| `/actuator/health` | Health geral |
| `/actuator/health/readiness` | Readiness probe |
| `/actuator/health/liveness` | Liveness probe |

## Configuracao no Deployment

Manifesto:

```text
../quarkus-opentelemetry/openshift/11-spring-app.yaml
```

Variaveis relevantes:

```yaml
- name: OTEL_EXPORTER_OTLP_ENDPOINT
  value: http://otel-collector.opentelemetry.svc:4318/v1/traces
- name: OTEL_SERVICE_NAMESPACE
  value: opentelemetry
- name: DEPLOYMENT_ENVIRONMENT
  value: openshift
```

Essas variaveis fazem a aplicacao enviar traces ao Collector criado pelo Red Hat build of OpenTelemetry.

## ServiceMonitor

O `ServiceMonitor` tambem esta em:

```text
../quarkus-opentelemetry/openshift/11-spring-app.yaml
```

Ele instrui o Prometheus do OpenShift a coletar:

```text
/actuator/prometheus
```

Referencia oficial relacionada:

- [Monitoring documentation](https://docs.redhat.com/en/documentation/openshift_container_platform/latest/html/monitoring/index)

## Build e deploy

A partir da raiz do repositorio:

```bash
oc apply -f quarkus-opentelemetry/openshift/11-spring-app.yaml
oc -n opentelemetry start-build spring-otel-observability --from-dir=spring-opentelemetry --follow
oc -n opentelemetry rollout status deploy/spring-otel-observability
```

## Validacao

Obter URL:

```bash
SPRING_URL=https://$(oc -n opentelemetry get route spring-otel-observability -o jsonpath='{.spec.host}')
echo "$SPRING_URL"
```

Testar endpoints:

```bash
curl "$SPRING_URL/spring/hello/ana"
curl "$SPRING_URL/spring/order/ana?items=4"
curl -i "$SPRING_URL/spring/error"
```

Ver metricas:

```bash
curl "$SPRING_URL/actuator/prometheus"
```

Filtrar metricas HTTP:

```bash
curl -s "$SPRING_URL/actuator/prometheus" | grep http_server_requests
```

Consulta TraceQL no Grafana:

```text
{ resource.service.name = "spring-otel-observability" }
```

Consulta para spans customizados:

```text
{ resource.service.name = "spring-otel-observability" && name =~ ".*spring\\.(order|payment).*" }
```

## Pontos para adaptação

- Ajustar `spring.application.name` para o nome real do servico.
- Ajustar `OTEL_EXPORTER_OTLP_ENDPOINT` para o Collector do ambiente.
- Definir `service.namespace` e `deployment.environment` conforme seu padroa.
- Rever `management.tracing.sampling.probability` antes de producao.
- Evitar tags/spans com dados sensiveis ou alta cardinalidade.
- Validar a versao Spring Boot/Micrometer conforme a matriz de suporte.

## Nota para uso

Este documento descreve a configuracao aplicada neste lab Spring Boot. Para producao, use-o como referencia inicial e confirme a implementacao final com as documentacoes oficiais da Red Hat para a plataforma, a matriz de suporte, os padroes internos da stack Spring e os requisitos de seguranca/compliance.
