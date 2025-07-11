package io.opentelemetry.example.graal;

import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.io.IOException;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

@Service
public class Pinger {
    private final WebClient webClient;

    public Pinger(WebClient.Builder webClientBuilder) throws IOException {
        String server = Optional.ofNullable(System.getenv("SERVER")).orElseThrow(() -> new IOException("SERVER is not set in the environment"));
        System.out.println("SERVER=" + server);
        this.webClient = webClientBuilder.baseUrl(server).build();
    }

    public String ping() {
        WebClient.ResponseSpec res = this.webClient.get().uri("/ping").retrieve();
        ResponseEntity<String> entity = res.toEntity(String.class).block();
        if (entity != null) {
            System.out.println("  HTTP status code: " + entity.getStatusCode());
            System.out.println("  headers: " + entity.getHeaders());
            return entity.getBody();
        }
        return "null";
    }

    public CompletableFuture<String> longpolling(int deadline) {
        return this.webClient.get()
                .uri("/longpolling/{deadline}", deadline)
                .exchangeToMono(clientResponse -> {
                    System.out.println("  HTTP status code: " + clientResponse.statusCode());
                    System.out.println("  headers: " + clientResponse.headers().asHttpHeaders());
                    return clientResponse.bodyToMono(String.class);
                })
                .toFuture();
    }
}
