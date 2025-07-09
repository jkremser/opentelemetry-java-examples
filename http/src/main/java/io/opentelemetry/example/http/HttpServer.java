/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.example.http;

import static io.opentelemetry.api.common.AttributeKey.stringKey;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.baggage.Baggage;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.incubator.trace.ExtendedSpanBuilder;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Context;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.Charset;
import java.util.Map;
import java.util.stream.Collectors;

public final class HttpServer {
  // It's important to initialize your OpenTelemetry SDK as early in your application's lifecycle as
  // possible.
  private static final OpenTelemetry openTelemetry = ExampleConfiguration.initOpenTelemetry(true, "server");
  private static final Tracer tracer =
      openTelemetry.getTracer("io.opentelemetry.example.http.HttpServer");

  private static final int port = 8080;
  private final com.sun.net.httpserver.HttpServer server;

  private HttpServer() throws IOException {
    this(port);
  }

  private HttpServer(int port) throws IOException {
    server = com.sun.net.httpserver.HttpServer.create(new InetSocketAddress(port), 0);
    // Test urls
    server.createContext("/", new HelloHandler());
    server.start();
    System.out.println("Server ready on http://127.0.0.1:" + port);
  }

  private static class HelloHandler implements HttpHandler {

    @Override
    public void handle(HttpExchange exchange) throws IOException {
      ((ExtendedSpanBuilder) tracer.spanBuilder("GET /"))
          .setParentFrom(
              openTelemetry.getPropagators(),
              exchange.getRequestHeaders().entrySet().stream()
                  .collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue().get(0))))
          .setSpanKind(SpanKind.SERVER)
          .startAndRun(
              () -> {
                // Set the Semantic Convention
                Span parentSpan = Span.current();
                parentSpan.updateName("handling-request");
                parentSpan.setAttribute("component", "http-call");
                parentSpan.setAttribute("http.method", "GET");
                /*
                 One of the following is required:
                 - http.scheme, http.host, http.target
                 - http.scheme, http.server_name, net.host.port, http.target
                 - http.scheme, net.host.name, net.host.port, http.target
                 - http.url
                */
                parentSpan.setAttribute("http.scheme", "http");
                parentSpan.setAttribute("http.host", "localhost:" + HttpServer.port);
                parentSpan.setAttribute("http.target", "/");
                // Process the request
                answer(exchange, parentSpan);
              });
    }

    private String calculateSomething() {
      Span.current().setAttribute("type", "calculation");
      try {
        // dummy
        Thread.sleep(800);
      } catch (Exception e) {
        System.out.println(e.getMessage());
      }
      return "24";
    }

    private void answer(HttpExchange exchange, Span parentSpan) throws IOException {
      // Generate an Event
      parentSpan.addEvent("Start Processing");

      // Process the request
      String response = "Hello KEDA!";
      exchange.sendResponseHeaders(200, response.length());
      OutputStream os = exchange.getResponseBody();
      os.write(response.getBytes(Charset.defaultCharset()));
      os.close();
      System.out.println("Served Client: " + exchange.getRemoteAddress());

      // Simulate some load
      Span childSpan = tracer.spanBuilder("inner server span")
              .setParent(Context.current().with(parentSpan))
              .startSpan();
      childSpan.addEvent("Started calculation");
      String result = calculateSomething();
      Attributes eventAttributesInner = Attributes.of(stringKey("answer"), result);
      childSpan.addEvent("Finished calculation", eventAttributesInner);
      childSpan.end();

      // Generate an Event with an attribute
      Attributes eventAttributes = Attributes.of(stringKey("answer"), response);
      parentSpan.addEvent("Finish Processing", eventAttributes);
    }
  }

  private void stop() {
    server.stop(0);
  }

  /**
   * Main method to run the example.
   *
   * @param args It is not required.
   * @throws Exception Something might go wrong.
   */
  public static void main(String[] args) throws Exception {
    System.out.println("version=" + ExampleConfiguration.VERSION);
    final HttpServer s = new HttpServer();
    // Gracefully close the server
    Runtime.getRuntime().addShutdownHook(new Thread(s::stop));
  }
}
