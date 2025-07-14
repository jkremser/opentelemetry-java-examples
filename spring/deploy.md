
# deploy jaeger w/ embedded OTLP receiver
```bash
helm repo add jaeger-all-in-one https://raw.githubusercontent.com/hansehe/jaeger-all-in-one/master/helm/charts
helm repo update jaeger-all-in-one
helm upgrade -i jaeger-all-in-one jaeger-all-in-one/jaeger-all-in-one --set enableHttpOpenTelemetryCollector=true
```

# deploy Kedify
```bash
cat <<VALUES | helm upgrade -i kedify-agent kedifykeda/kedify-agent --version=v0.2.14 -nkeda --create-namespace -f -
agent:
  kedifyServer: kedify-proxy.api.dev.kedify.io:443
  orgId: ${KEDIFY_ORG_ID}
  apiKey: ${KEDIFY_API_KEY}
clusterName: spring
keda:
  enabled: true
keda-add-ons-http:
  enabled: true
  interceptor:
    replicas:
      max: 1
    additionalEnvVars:
    - name: OTEL_EXPORTER_OTLP_TRACES_ENABLED
      value: "true"
    - name: OTEL_EXPORTER_OTLP_TRACES_ENDPOINT
      value: "http://jaeger-all-in-one.default.svc:4317"
    - name: OTEL_EXPORTER_OTLP_TRACES_PROTOCOL
      value: "grpc"
    - name: OTEL_EXPORTER_OTLP_TRACES_INSECURE
      value: "true"
VALUES
```

# server app
```bash
kubectl create deploy spring-server --image=docker.io/jkremser/springboot:tracing --port=8080
kubectl expose deploy spring-server --name=otel-spring-server-fallback --type=ClusterIP --port=8080 --target-port=8080
kubectl expose deploy spring-server --name=otel-spring-server --type=ClusterIP --port=8080 --target-port=8080 --dry-run=client -oyaml | yq 'del(.spec.selector)' | kubectl apply -f -

kubectl set env deploy/spring-server \
   OTEL_EXPORTER_OTLP_TRACES_ENDPOINT="http://jaeger-all-in-one.default.svc:4317" \
   SERVER="" \
   SERVICE_NAME=server
```

# client app
```bash
kubectl create deploy spring-client --image=docker.io/jkremser/springboot:tracing
kubectl set env deploy/spring-client \
  SERVER="spring-server:8080" \
  SLEEP_MS="300000" \
  OTEL_EXPORTER_OTLP_TRACES_ENDPOINT="http://jaeger-all-in-one.default.svc:4317" \
  SERVICE_NAME=client
```

# workaround
```bash
kubectl set env deploy/spring-client SERVER="kedify-proxy:8080"
```

# ScaledObject
```bash
cat <<SO | kubectl apply -f -
kind: ScaledObject
apiVersion: keda.sh/v1alpha1
metadata:
  name: spring-server
spec:
  scaleTargetRef:
    apiVersion: apps/v1
    kind: Deployment
    name: spring-server
  cooldownPeriod: 5
  minReplicaCount: 1
  maxReplicaCount: 2
  pollingInterval: 30
  fallback:
    failureThreshold: 2
    replicas: 1
  advanced:
    restoreToOriginalReplicaCount: true
    horizontalPodAutoscalerConfig:
      behavior:
        scaleDown:
          stabilizationWindowSeconds: 5
  triggers:
    - type: kedify-http
      metadata:
        hosts: spring-server
        service: spring-server
        port: '8080'
        scalingMetric: requestRate
        targetValue: '1'
        granularity: 1s
        window: 10s
        trafficAutowire: service
        fallbackService: spring-server-fallback
SO
```

# check the Jaeger
```bash
(kubectl port-forward svc/jaeger-all-in-one 16686 &> /dev/null)& pf_pid=$!
(sleep $[10*60] && kill ${pf_pid})&
open http://localhost:16686
```

# HTTP Interceptor Tracing

```bash
k scale deployment otel-tracing-client --replicas=0
# allow scale to 0
k patch so otel-tracing-server --type=json -p '[{"op":"replace","path":"/spec/minReplicaCount","value":0}]'
# make 1 call per 10 mins
kubectl set env deploy/otel-tracing-client SLEEP_MS="$[10*60*1000]"

# intercepor should have a log msg:
# 2025-07-03T14:52:18Z	INFO	LoggingMiddleware	10.42.0.24:45856 - - [03/Jul/2025:14:52:11 +0000] "GET / HTTP/1.1" 200 11 "" "Java/21.0.4"
# and wake up the server

# to make a request
k rollout restart deploy/otel-tracing-client

# debug
kubectl run dump --image=nixery.dev/shell/curl/jq --restart=Never --command -- /bin/sh -c "sleep infinity"
kubectl exec -it dump -- curl kedify-proxy-admin:9901/config_dump | jq '.configs[]?.dynamic_active_clusters[]?.cluster.load_assignment.endpoints[]?.lb_endpoints[]?.endpoint.address.socket_address | "\(.address):\(.port_value)"'


k port-forward svc/otel-tracing-server-fallback 8080
or
k port-forward svc/otel-tracing-server 8080
curl localhost:8080


wget --header="host: otel-tracing-server" -qO- http://otel-tracing-server:8080
k exec -ti dump -- sh
curl -H 'host: otel-tracing-server' http://otel-tracing-server.default.svc.cluster.local:8080
```
