package io.opentelemetry.example.graal;

import io.netty.channel.ChannelOption;
import io.netty.channel.socket.nio.NioChannelOption;
import jdk.net.ExtendedSocketOptions;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;
import reactor.netty.resources.ConnectionProvider;

import java.io.IOException;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

@Service
public class Pinger {
    private final WebClient webClient;
    private final Optional<String> maybeHostHeader;

    public Pinger(WebClient.Builder webClientBuilder) throws IOException {
        String server = Optional.ofNullable(System.getenv("SERVER")).orElseThrow(() -> new IOException("SERVER is not set in the environment"));
        System.out.println("SERVER=" + server);

        this.maybeHostHeader = Optional.ofNullable(System.getenv("HOST_HEADER")).filter(s -> !s.isEmpty());
        System.out.println("SERVER=" + server);

        ConnectionProvider provider =
                ConnectionProvider.builder("non-default")
                        .maxConnections(3000)
                        .maxIdleTime(Duration.ofSeconds(400))
                        .maxLifeTime(Duration.ofSeconds(400))
                        .disposeTimeout(Duration.ofSeconds(400))
                        .maxConnectionPools(1)
                        .metrics(true)
                        .build();
        HttpClient httpClient = HttpClient.create(provider)
                .responseTimeout(Duration.ofSeconds(400))
//                .option(NioChannelOption.of(ExtendedSocketOptions.TCP_KEEPIDLE), 300)
//                .option(NioChannelOption.of(ExtendedSocketOptions.TCP_KEEPINTERVAL), 60)
//                .option(NioChannelOption.of(ExtendedSocketOptions.TCP_KEEPCOUNT), 8)
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 40000);
        this.webClient = webClientBuilder.baseUrl(server)
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .build();
    }

    public String ping() {
        WebClient.RequestHeadersSpec<?> call = this.webClient.get()
                .uri("/ping");
        maybeHostHeader.ifPresent(hostHeader -> {
            System.out.println("  HOST header explicitly set to: " + hostHeader);
            call.header(HttpHeaders.HOST, hostHeader);
        });
        WebClient.ResponseSpec res = call.retrieve();
        ResponseEntity<String> entity = res.toEntity(String.class).block();
        if (entity != null) {
            System.out.println("  HTTP status code: " + entity.getStatusCode());
            System.out.println("  headers: " + entity.getHeaders());
            return entity.getBody();
        }
        return "null";
    }

    public CompletableFuture<String> longpolling(int deadline) {
        WebClient.RequestHeadersSpec<?> call = this.webClient.get()
                .uri("/longpolling/{deadline}", deadline);
        maybeHostHeader.ifPresent(hostHeader -> {
            System.out.println("  HOST header explicitly set to: " + hostHeader);
            call.header(HttpHeaders.HOST, hostHeader);
        });

        return call.exchangeToMono(clientResponse -> {
                    System.out.println("  HTTP status code: " + clientResponse.statusCode());
                    System.out.println("  headers: " + clientResponse.headers().asHttpHeaders());
                    return clientResponse.bodyToMono(String.class);
                })
                .toFuture();
    }
}
