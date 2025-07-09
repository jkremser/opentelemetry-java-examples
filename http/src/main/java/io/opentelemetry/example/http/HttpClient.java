/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.example.http;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.baggage.Baggage;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.incubator.propagation.ExtendedContextPropagators;
import io.opentelemetry.api.incubator.trace.ExtendedSpanBuilder;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.semconv.HttpAttributes;
import io.opentelemetry.semconv.UrlAttributes;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.Charset;
import java.util.Optional;

import static io.opentelemetry.api.common.AttributeKey.stringKey;

public final class HttpClient {

  // it's important to initialize the OpenTelemetry SDK as early in your applications lifecycle as
  // possible.
  private static final OpenTelemetry openTelemetry = ExampleConfiguration.initOpenTelemetry(true, "client");

  private static final Tracer tracer =
      openTelemetry.getTracer("io.opentelemetry.example.http.HttpClient");

  private void makeRequest(String server) throws IOException, URISyntaxException {
    URL url = new URL("http://" + server);
    HttpURLConnection con = (HttpURLConnection) url.openConnection();

    int status = 0;
    StringBuilder content = new StringBuilder();

    // Name convention for the Span is not yet defined.
    // See: https://github.com/open-telemetry/opentelemetry-specification/issues/270
    Span parentSpan = tracer.spanBuilder("/").setSpanKind(SpanKind.CLIENT).startSpan();
    try (Scope scope = parentSpan.makeCurrent()) {
      parentSpan.setAttribute(HttpAttributes.HTTP_REQUEST_METHOD, "GET");
      parentSpan.setAttribute("component", "http-call");
      /*
       Only one of the following is required
         - http.url
         - http.scheme, http.host, http.target
         - http.scheme, peer.hostname, peer.port, http.target
         - http.scheme, peer.ip, peer.port, http.target
      */
      URI uri = url.toURI();
      url =
          new URI(
                  uri.getScheme(),
                  null,
                  uri.getHost(),
                  uri.getPort(),
                  uri.getPath(),
                  uri.getQuery(),
                  uri.getFragment())
              .toURL();

      parentSpan.setAttribute(UrlAttributes.URL_FULL, url.toString());
      Span childSpan = tracer.spanBuilder("inner client span")
              .setParent(Context.current().with(parentSpan))
              .startSpan();
      childSpan.setAttribute("type", "calculation");
      childSpan.addEvent("Started calculation");
      String result = calculateSomething();
      Attributes eventAttributes = Attributes.of(stringKey("answer"), result);
      childSpan.addEvent("Finished calculation", eventAttributes);
      childSpan.end();

      // Inject the request with the current Context/Span.
      ExtendedContextPropagators.getTextMapPropagationContext(openTelemetry.getPropagators())
          .forEach(con::setRequestProperty);

      try {
        // Process the request
        con.setRequestMethod("GET");
        con.setRequestProperty ("Host", "otel-tracing-server");
        status = con.getResponseCode();
        BufferedReader in =
            new BufferedReader(
                new InputStreamReader(con.getInputStream(), Charset.defaultCharset()));
        String inputLine;
        while ((inputLine = in.readLine()) != null) {
          content.append(inputLine);
        }
        in.close();
      } catch (Exception e) {
        parentSpan.setStatus(StatusCode.ERROR, "HTTP Code: " + status);
      }
    } finally {
      parentSpan.end();
    }

    // Output the result of the request
    System.out.println("Response Code: " + status);
    System.out.println("Response Msg: " + content);
  }

  private String calculateSomething() {
    try {
      // dummy
      Thread.sleep(500);
    } catch (Exception e) {
      System.out.println(e.getMessage());
    }
    return "42";
  }

  /**
   * Main method to run the example.
   *
   * @param args It is not required.
   */
  public static void main(String[] args) throws IOException {
    System.out.println("version=" + ExampleConfiguration.VERSION);
    String server = Optional.ofNullable(System.getenv("SERVER")).orElseThrow(() -> new IOException("SERVER is not set in the environment"));
    String sleepMs = Optional.ofNullable(System.getenv("SLEEP_MS")).orElseThrow(() -> new IOException("SLEEP_MS is not set in the environment"));
    System.out.println("SERVER=" + server);
    System.out.println("SLEEP_MS=" + sleepMs);
    int sleepMsInt;
    try {
      sleepMsInt = Integer.parseInt(sleepMs);
    } catch (NumberFormatException e) {
      System.out.println("Unable to parse SLEEP_MS, defaulting to 5000ms");
      sleepMsInt = 5000;
    }
    final int sleepMsIntFinal = sleepMsInt;

    // required in order to be able to set the Host HTTP header
    System.setProperty("sun.net.http.allowRestrictedHeaders", "true");
    HttpClient httpClient = new HttpClient();

    // Perform request every 5s
    Thread t =
        new Thread(
            () -> {
              while (true) {
                try {
                  httpClient.makeRequest(server);
                  Thread.sleep(sleepMsIntFinal);
                } catch (Exception e) {
                  System.out.println(e.getMessage());
                }
              }
            });
    t.start();
  }
}
