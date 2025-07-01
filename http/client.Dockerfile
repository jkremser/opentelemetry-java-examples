FROM --platform=$TARGETARCH alpine/java:21
COPY ./build/libs/opentelemetry-examples-http-0.1.0-SNAPSHOT-all.jar /all.jar
ENV SERVER=localhost:8080
ENV SLEEP_MS=5000
WORKDIR /
USER 65534:65534
CMD ["java", "-cp", "/all.jar", "io.opentelemetry.example.http.HttpClient"]
