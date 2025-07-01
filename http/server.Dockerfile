FROM --platform=$TARGETARCH alpine/java:21
COPY ./build/libs/opentelemetry-examples-http-0.1.0-SNAPSHOT-all.jar /all.jar
WORKDIR /
EXPOSE 8080
USER 65534:65534
CMD ["java", "-cp", "/all.jar", "io.opentelemetry.example.http.HttpServer"]
