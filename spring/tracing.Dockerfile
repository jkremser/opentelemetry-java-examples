FROM --platform=$TARGETARCH alpine/java:21
COPY ./build/libs/opentelemetry-examples-spring-0.1.0-SNAPSHOT.jar /app.jar
# ENV SERVER=http://localhost:8080
ENV SLEEP_MS=5000
ENV OTEL_LOGS_EXPORTER=logging
ENV OTEL_EXPORTER_OTLP_TRACES_ENDPOINT=http://jaeger-all-in-one:4317
ENV OTEL_EXPORTER_OTLP_TRACES_PROTOCOL=grpc
ENV SERVICE_NAME=spring-client-server
WORKDIR /
USER 65534:65534
CMD ["java", "-jar", "-Dspring.application.name=${SERVICE_NAME}", "/app.jar"]
