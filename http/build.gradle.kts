plugins {
    id("java")
}

description = "OpenTelemetry Examples for HTTP"
val moduleName by extra { "io.opentelemetry.examples.http" }

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(8))
    }
}

dependencies {
    implementation("io.opentelemetry:opentelemetry-api")
    implementation("io.opentelemetry:opentelemetry-sdk")
    implementation("io.opentelemetry:opentelemetry-exporter-logging")
    implementation("io.opentelemetry:opentelemetry-exporter-otlp")
    implementation("io.opentelemetry:opentelemetry-extension-trace-propagators")

    //alpha modules
    implementation("io.opentelemetry.semconv:opentelemetry-semconv")
    implementation("io.opentelemetry:opentelemetry-sdk-extension-autoconfigure")
    implementation("io.opentelemetry:opentelemetry-api-incubator")
}
