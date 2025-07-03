/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.example.http;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator;
import io.opentelemetry.context.propagation.ContextPropagators;
import io.opentelemetry.exporter.otlp.trace.OtlpGrpcSpanExporter;
import io.opentelemetry.exporter.logging.LoggingSpanExporter;
import io.opentelemetry.extension.trace.propagation.B3Propagator;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.SpanProcessor;
import io.opentelemetry.sdk.trace.export.BatchSpanProcessor;
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor;

import java.util.Optional;
import java.util.concurrent.TimeUnit;

/**
 * All SDK management takes place here, away from the instrumentation code, which should only access
 * the OpenTelemetry APIs.
 */
class ExampleConfiguration {

  /**
   * Initializes the OpenTelemetry SDK with a logging span exporter and the W3C Trace Context
   * propagator.
   *
   * @return A ready-to-use {@link OpenTelemetry} instance.
   */
  static OpenTelemetry initOpenTelemetry(boolean report) {
    Optional<String> otlpEndpoint = Optional.ofNullable(System.getenv("OTEL_EXPORTER_OTLP_TRACES_ENDPOINT"));
    SpanProcessor spanProcessor;
    if (report && otlpEndpoint.isPresent()) {
      spanProcessor = BatchSpanProcessor.builder(
                      OtlpGrpcSpanExporter.builder()
                              .setTimeout(2, TimeUnit.SECONDS)
                              .setEndpoint(otlpEndpoint.get())
                              .build())
              .setScheduleDelay(100, TimeUnit.MILLISECONDS)
              .build();
    } else {
      spanProcessor = SimpleSpanProcessor.create(new LoggingSpanExporter());
    }

    SdkTracerProvider sdkTracerProvider =
        SdkTracerProvider.builder()
            .addSpanProcessor(spanProcessor)
            .build();
    OpenTelemetrySdk sdk =
        OpenTelemetrySdk.builder()
            .setTracerProvider(sdkTracerProvider)
            .setPropagators(ContextPropagators.create(B3Propagator.injectingMultiHeaders()))
            .setPropagators(ContextPropagators.create(W3CTraceContextPropagator.getInstance()))
            .build();

    Runtime.getRuntime().addShutdownHook(new Thread(sdkTracerProvider::close));
    return sdk;
  }
}
