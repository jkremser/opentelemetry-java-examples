#!/bin/bash
../gradlew shadowJar
IMG_REPO=docker.io/jkremser/otel-tracing
docker build --push --platform linux/amd64,linux/arm64 -f client.Dockerfile  --tag ${IMG_REPO}-client .
docker build --push --platform linux/amd64,linux/arm64 -f server.Dockerfile  --tag ${IMG_REPO}-server .
