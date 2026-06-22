#!/usr/bin/env bash
set -euo pipefail

NAMESPACE="${NAMESPACE:-opentelemetry}"
IMAGE_NAME="${IMAGE_NAME:-quarkus-otel-observability}"
IMAGE_TAG="${IMAGE_TAG:-latest}"
OC="${OC:-oc}"

log() {
  printf '\n==> %s\n' "$1"
}

need() {
  if ! command -v "$1" >/dev/null 2>&1; then
    printf 'Missing required command: %s\n' "$1" >&2
    exit 1
  fi
}

wait_for_crd() {
  local resource="$1"
  local resources
  resources="$("$OC" api-resources --cached=false)"
  if ! grep -qi "$resource" <<<"$resources"; then
    printf 'Missing required OpenShift API resource matching: %s\n' "$resource" >&2
    printf 'Install the related Operator from OperatorHub, then run this script again.\n' >&2
    exit 1
  fi
}

log "Checking local tools"
if ! command -v "$OC" >/dev/null 2>&1; then
  if [[ "$OC" == "oc" && -x ./oc ]]; then
    OC="./oc"
  else
    printf 'Missing required command: %s\n' "$OC" >&2
    exit 1
  fi
fi

log "Checking OpenShift login"
"$OC" whoami >/dev/null

log "Checking required Operator CRDs"
wait_for_crd "opentelemetrycollectors"
wait_for_crd "tempostacks"
wait_for_crd "grafanas"
wait_for_crd "grafanadatasources"
wait_for_crd "objectbucketclaims"
wait_for_crd "noobaas"
wait_for_crd "servicemonitors"

log "Creating namespace"
"$OC" apply -f openshift/01-namespace.yaml
"$OC" project "$NAMESPACE"

log "Creating ODF/NooBaa lab object storage"
"$OC" apply -f openshift/04-odf-object-storage.yaml
"$OC" -n openshift-storage wait --for=jsonpath='{.status.phase}'=Ready noobaa/noobaa --timeout=15m
"$OC" -n "$NAMESPACE" wait --for=jsonpath='{.status.phase}'=Bound obc/tempo-bucket --timeout=10m

log "Creating Tempo object storage secret from ODF ObjectBucketClaim"
BUCKET_NAME="$("$OC" -n "$NAMESPACE" get configmap tempo-bucket -o jsonpath='{.data.BUCKET_NAME}')"
BUCKET_HOST="$("$OC" -n "$NAMESPACE" get configmap tempo-bucket -o jsonpath='{.data.BUCKET_HOST}')"
BUCKET_PORT="$("$OC" -n "$NAMESPACE" get configmap tempo-bucket -o jsonpath='{.data.BUCKET_PORT}')"
ACCESS_KEY="$("$OC" -n "$NAMESPACE" get secret tempo-bucket -o jsonpath='{.data.AWS_ACCESS_KEY_ID}' | base64 -d)"
SECRET_KEY="$("$OC" -n "$NAMESPACE" get secret tempo-bucket -o jsonpath='{.data.AWS_SECRET_ACCESS_KEY}' | base64 -d)"

"$OC" -n "$NAMESPACE" create secret generic tempo-object-storage \
  --from-literal=endpoint="https://${BUCKET_HOST}:${BUCKET_PORT}" \
  --from-literal=bucket="${BUCKET_NAME}" \
  --from-literal=access_key_id="${ACCESS_KEY}" \
  --from-literal=access_key_secret="${SECRET_KEY}" \
  --dry-run=client -o yaml | "$OC" apply -f -

log "Creating TempoStack"
"$OC" apply -f openshift/05-tempostack.yaml
"$OC" -n "$NAMESPACE" get tempostack

log "Creating Grafana and Tempo datasource"
"$OC" apply -f openshift/10-grafana-prometheus-rbac.yaml
"$OC" apply -f openshift/06-grafana-operator.yaml
"$OC" -n "$NAMESPACE" wait --for=create serviceaccount/grafana-sa --timeout=120s
"$OC" -n "$NAMESPACE" wait --for=create route/grafana-route --timeout=120s
"$OC" -n "$NAMESPACE" rollout status deploy/grafana-deployment --timeout=180s
PROMETHEUS_TOKEN="$("$OC" create token grafana-sa -n "$NAMESPACE" --duration=8760h)"
"$OC" -n "$NAMESPACE" create secret generic grafana-prometheus-token \
  --from-literal=bearerToken="Bearer ${PROMETHEUS_TOKEN}" \
  --dry-run=client -o yaml | "$OC" apply -f -
"$OC" -n "$NAMESPACE" get grafana
"$OC" -n "$NAMESPACE" get grafanadatasource

upsert_prometheus_datasource() {
  local name="$1"
  local uid="$2"
  local payload
  payload="$(jq -n \
    --arg name "$name" \
    --arg uid "$uid" \
    --arg token "Bearer ${PROMETHEUS_TOKEN}" \
    '{
      name: $name,
      uid: $uid,
      type: "prometheus",
      access: "proxy",
      url: "https://thanos-querier.openshift-monitoring.svc:9091",
      isDefault: false,
      jsonData: {
        httpMethod: "POST",
        httpHeaderName1: "Authorization",
        tlsSkipVerify: true,
        timeInterval: "15s"
      },
      secureJsonData: {
        httpHeaderValue1: $token
      }
    }')"

  if curl -fsS -u admin:admin "https://${GRAFANA_HOST}/api/datasources/uid/${uid}" >/dev/null 2>&1; then
    curl -fsS -u admin:admin -H 'Content-Type: application/json' -X PUT \
      "https://${GRAFANA_HOST}/api/datasources/uid/${uid}" \
      -d "$payload" >/dev/null
  else
    curl -fsS -u admin:admin -H 'Content-Type: application/json' -X POST \
      "https://${GRAFANA_HOST}/api/datasources" \
      -d "$payload" >/dev/null
  fi
}

log "Creating Grafana Prometheus datasources through Grafana API"
GRAFANA_HOST="$("$OC" -n "$NAMESPACE" get route grafana-route -o jsonpath='{.spec.host}')"
upsert_prometheus_datasource "prometheus" "prometheus"
upsert_prometheus_datasource "Prometheus" "Prometheus"

log "Creating Grafana dashboard"
"$OC" apply -f openshift/09-grafana-dashboard.yaml
"$OC" -n "$NAMESPACE" get grafanadashboard quarkus-otel-overview

log "Creating OpenTelemetry Collector"
"$OC" apply -f openshift/04-otel-collector.yaml
"$OC" -n "$NAMESPACE" get opentelemetrycollector

log "Building Quarkus application inside OpenShift"
"$OC" apply -f openshift/08-buildconfig.yaml
"$OC" -n "$NAMESPACE" start-build quarkus-otel-observability --from-dir=. --follow

log "Deploying application"
"$OC" apply -f openshift/02-app.yaml
"$OC" -n "$NAMESPACE" rollout status deploy/quarkus-otel-observability

log "Applying OpenShift monitoring for application metrics"
"$OC" apply -f openshift/07-user-workload-monitoring.yaml
"$OC" apply -f openshift/03-servicemonitor.yaml

log "Routes"
"$OC" -n "$NAMESPACE" get route

APP_URL="https://$("$OC" -n "$NAMESPACE" get route quarkus-otel-observability -o jsonpath='{.spec.host}')"
printf '\nApplication URL: %s\n' "$APP_URL"

log "Generating sample telemetry"
curl -fsS "$APP_URL/observability/help" >/dev/null
curl -fsS "$APP_URL/observability/hello/lucas" >/dev/null
curl -fsS "$APP_URL/observability/checkout/ana?items=4" >/dev/null
curl -fsS "$APP_URL/observability/slow?ms=900" >/dev/null
curl -fsS -o /dev/null -w 'Intentional error endpoint returned HTTP %{http_code}\n' "$APP_URL/observability/error" || true

log "Useful checks"
printf '%s -n %s get pods\n' "$OC" "$NAMESPACE"
printf '%s -n %s logs deploy/otel-collector\n' "$OC" "$NAMESPACE"
printf 'Open Grafana route from: %s -n %s get route\n' "$OC" "$NAMESPACE"
