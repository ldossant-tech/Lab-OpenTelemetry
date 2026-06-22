# Spring Boot OpenTelemetry demo

Aplicacao Spring Boot minima para comparar com a aplicacao Quarkus do lab.

Ela demonstra onde configurar:

- traces via OpenTelemetry/OTLP;
- metricas via Spring Actuator + Prometheus;
- spans customizados no codigo com `io.micrometer.tracing.Tracer`.

## Rotas

- `/spring/hello/{name}`
- `/spring/order/{customer}?items=3`
- `/spring/error`
- `/actuator/prometheus`
- `/actuator/health`

## Configuracao principal

Arquivo:

```text
src/main/resources/application.properties
```

Traces:

```properties
management.tracing.enabled=true
management.tracing.sampling.probability=1.0
management.otlp.tracing.endpoint=${OTEL_EXPORTER_OTLP_ENDPOINT:http://localhost:4318/v1/traces}
```

Metricas:

```properties
management.endpoints.web.exposure.include=health,info,prometheus
management.prometheus.metrics.export.enabled=true
```

Endpoint de metricas:

```text
/actuator/prometheus
```

## Executar local

```bash
mvn spring-boot:run
```

## Gerar dados

```bash
APP_URL=http://localhost:8080

curl "$APP_URL/spring/hello/ana"
curl "$APP_URL/spring/order/ana?items=4"
curl -i "$APP_URL/spring/error"
curl "$APP_URL/actuator/prometheus"
```
