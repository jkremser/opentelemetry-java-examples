#!/bin/bash
../gradlew bootJar
IMG_REPO=docker.io/jkremser/springboot2
docker build --push --platform linux/amd64,linux/arm64 -f tracing.Dockerfile  --tag ${IMG_REPO}:tracing .
docker build --push --platform linux/amd64,linux/arm64 -f notracing.Dockerfile  --tag ${IMG_REPO}:notracing .
