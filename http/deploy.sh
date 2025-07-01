#!/bin/bash

# deploy Kedify


# server
kubectl create deploy otel-tracing-server --image=docker.io/jkremser/otel-tracing-server --port=8080
kubectl expose deploy otel-tracing-server --name=otel-tracing-server-fallback --type=ClusterIP --port=8080 --target-port=8080
kubectl get svc otel-tracing-server-fallback -oyaml | yq 'del(.spec.clusterIPs, .spec.clusterIP, .spec.selector, .metadata.resourceVersion, .metadata)' | yq '.metadata.name="otel-tracing-server"' | kubectl apply -f -

# client
kubectl create deploy otel-tracing-client --image=docker.io/jkremser/otel-tracing-client
kubectl set env deploy otel-tracing-client SERVER="otel-tracing-server.default.svc:8080"

# ScaledObject
cat <<SO | kubectl apply -f -
kind: ScaledObject
apiVersion: keda.sh/v1alpha1
metadata:
  name: otel-tracing-server
spec:
  scaleTargetRef:
    apiVersion: apps/v1
    kind: Deployment
    name: otel-tracing-server
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
        hosts: otel-tracing-server
        service: otel-tracing-server
        port: '8080'
        scalingMetric: requestRate
        targetValue: '1'
        granularity: 1s
        window: 10s
        trafficAutowire: service
        fallbackService: otel-tracing-server-fallback
SO
