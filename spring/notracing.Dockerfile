FROM --platform=$TARGETARCH alpine/java:21
COPY ./build/libs/opentelemetry-examples-spring-0.1.0-SNAPSHOT.jar /app.jar
ENV SERVER=http://localhost:8080
ENV SLEEP_MS=5000
WORKDIR /
USER 65534:65534
CMD ["java", "-Dotel.sdk.disabled=true", "-jar", "/app.jar"]
