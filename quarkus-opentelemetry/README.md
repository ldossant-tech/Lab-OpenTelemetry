# OpenTelemetry Lab no OpenShift

Lab para apresentar como o OpenTelemetry atua em uma aplicacao Quarkus rodando no OpenShift.

Namespace usado no lab:

```text
opentelemetry
```

Arquitetura:

```text
Quarkus
  -> OTLP
Red Hat build of OpenTelemetry Collector
  -> OTLP
Grafana Tempo
  -> Grafana

Quarkus
  -> /q/metrics
Prometheus Operator / OpenShift User Workload Monitoring
  -> Grafana
```

Componentes por operator:

- Red Hat build of OpenTelemetry
- Tempo Operator
- Grafana Operator
- OpenShift Data Foundation, usado como S3 de laboratorio para o Tempo
- Prometheus Operator do OpenShift, via User Workload Monitoring e `ServiceMonitor`

O Grafana recebe tambem um dashboard pronto:

```text
OpenTelemetry Lab - Traces e metricas
```

Os manifests de `Subscription` para Grafana estao em `openshift/00-operators.yaml`. ODF pode ser instalado com `openshift/00-odf-operator.yaml`. RHBO e Tempo ja podem existir no cluster; se nao existirem, instale-os pelo OperatorHub antes do lab.

A documentacao passo a passo esta em:

[docs/lab-openshift.md](docs/lab-openshift.md)

O que foi inserido na aplicacao para traces e metricas esta em:

[docs/instrumentacao-aplicacao.md](docs/instrumentacao-aplicacao.md)

Quando as ferramentas locais estiverem instaladas e o `oc` estiver logado no cluster, o lab pode ser aplicado com:

```bash
chmod +x scripts/deploy-lab.sh
./scripts/deploy-lab.sh
```

Rotas principais da aplicacao:

- `/observability/help`
- `/observability/hello/{name}`
- `/observability/inventory`
- `/observability/checkout/{customer}?items=3`
- `/observability/slow?ms=800`
- `/observability/error`
- `/q/metrics`

## Aplicacao Spring Boot para comparacao

Tambem ha uma aplicacao Spring Boot minima em:

```text
spring-opentelemetry/
```

Ela serve para demonstrar onde o Spring configura traces e metricas:

```text
spring-opentelemetry/src/main/resources/application.properties
```

Traces:

```properties
management.tracing.enabled=true
management.tracing.sampling.probability=1.0
management.otlp.tracing.endpoint=${OTEL_EXPORTER_OTLP_ENDPOINT:http://localhost:4318/v1/traces}
```

Metricas Prometheus:

```properties
management.endpoints.web.exposure.include=health,info,prometheus
management.prometheus.metrics.export.enabled=true
```

Rotas Spring:

- `/spring/hello/{name}`
- `/spring/order/{customer}?items=3`
- `/spring/error`
- `/actuator/prometheus`

Para subir no OpenShift, apos `oc login`:

```bash
./oc apply -f openshift/11-spring-app.yaml
./oc start-build spring-otel-observability -n opentelemetry --from-dir=spring-opentelemetry --follow
./oc rollout status deploy/spring-otel-observability -n opentelemetry
./oc get route spring-otel-observability -n opentelemetry
```

```bash
APP_URL="https://quarkus-otel-observability-opentelemetry.apps.cluster-bzw22.bzw22.sandbox697.opentlc.com"

for i in {1..30}; do
  curl -s "$APP_URL/observability/hello/user-$i" > /dev/null
  curl -s "$APP_URL/observability/inventory" > /dev/null
  curl -s "$APP_URL/observability/checkout/customer-$i" > /dev/null
  curl -s "$APP_URL/observability/slow" > /dev/null
  curl -s -o /dev/null -w "error request: HTTP %{http_code}\n" "$APP_URL/observability/error"
  sleep 1
done
```
