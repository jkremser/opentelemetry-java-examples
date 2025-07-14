#!/bin/bash
set -e

command -v figlet &> /dev/null && figlet OTel Tracing + Kedify Proxy + Spring Boot
[ -z "${KEDIFY_ORG_ID}" ] && echo "Set KEDIFY_ORG_ID env variable" && exit 1
[ -z "${KEDIFY_API_KEY}" ] && echo "Set KEDIFY_API_KEY env variable" && exit 1

# setup cluster
k3d cluster delete tracing-spring &> /dev/null
k3d cluster create tracing-spring -p "8080:30042@server:0"

# deploy jaeger w/ embedded OTLP receiver
helm repo add jaeger-all-in-one https://raw.githubusercontent.com/hansehe/jaeger-all-in-one/master/helm/charts --force-update
helm repo update jaeger-all-in-one
helm upgrade -i jaeger-all-in-one jaeger-all-in-one/jaeger-all-in-one --set enableHttpOpenTelemetryCollector=true
kubectl expose svc/jaeger-all-in-one --dry-run=client -oyaml --port 16686 --name=jaeger-np --type=NodePort | yq '.spec.ports[0].nodePort=30042' | kubectl apply -f -

# deploy Kedify
cat <<VALUES | helm upgrade -i kedify-agent kedifykeda/kedify-agent --version=v0.2.14 -nkeda --create-namespace -f -
agent:
  kedifyServer: kedify-proxy.api.dev.kedify.io:443
  orgId: ${KEDIFY_ORG_ID}
  apiKey: ${KEDIFY_API_KEY}
clusterName: spring2
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

# server app
kubectl create deploy spring-server --image=docker.io/jkremser/springboot:tracing --port=8080
kubectl expose deploy spring-server --name=otel-spring-server-fallback --type=ClusterIP --port=8080 --target-port=8080
kubectl expose deploy spring-server --name=otel-spring-server --type=ClusterIP --port=8080 --target-port=8080 --dry-run=client -oyaml | yq 'del(.spec.selector)' | kubectl apply -f -
kubectl rollout status deploy/spring-server

kubectl set env deploy/spring-server \
   OTEL_EXPORTER_OTLP_TRACES_ENDPOINT="http://jaeger-all-in-one.default.svc:4317" \
   SERVER="" \
   SERVICE_NAME=server

# client app
kubectl create deploy spring-client --image=docker.io/jkremser/springboot:tracing
kubectl set env deploy/spring-client \
  SERVER="http://otel-spring-server:8080" \
  SLEEP_MS="300000" \
  OTEL_EXPORTER_OTLP_TRACES_ENDPOINT="http://jaeger-all-in-one.default.svc:4317" \
  SERVICE_NAME=client

# workaround
kubectl set env deploy/spring-client SERVER="http://kedify-proxy:8080"
kubectl rollout status deploy/spring-client

# ScaledObject
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
        hosts: otel-spring-server
        service: otel-spring-server
        port: '8080'
        scalingMetric: requestRate
        targetValue: '1'
        granularity: 1s
        window: 10s
        trafficAutowire: service
        fallbackService: otel-spring-server-fallback
SO

# check the Jaeger
kubectl rollout status statefulsets/jaeger-all-in-one && echo "Jaeger is ready.." && sleep 5
open http://localhost:8080
