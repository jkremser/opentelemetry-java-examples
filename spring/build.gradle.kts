import org.springframework.boot.gradle.plugin.SpringBootPlugin

plugins {
    id("java")
    id("com.github.johnrengelman.shadow") version "8.1.1"
    id("org.springframework.boot") version "3.5.3"
    id("org.springdoc.openapi-gradle-plugin") version "1.8.0"
}

apply(plugin = "io.spring.dependency-management")


description = "OpenTelemetry Example for Spring images"
val moduleName by extra { "io.opentelemetry.examples.spring" }

dependencies {
    implementation(platform(SpringBootPlugin.BOM_COORDINATES))
    implementation(platform("io.opentelemetry.instrumentation:opentelemetry-instrumentation-bom:2.17.0"))
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-webflux")
    implementation("io.opentelemetry.instrumentation:opentelemetry-spring-boot-starter")
    implementation("io.netty:netty-codec-http2")
    implementation("io.netty:netty-transport-classes-epoll")

    // for otelCustomizer in Application.java
    implementation("io.opentelemetry.contrib:opentelemetry-samplers:1.46.0-alpha")
    implementation("io.opentelemetry:opentelemetry-sdk-logs")

    // for metrics
    implementation("io.micrometer:micrometer-registry-prometheus")
    implementation("io.micrometer:micrometer-registry-otlp")
}
