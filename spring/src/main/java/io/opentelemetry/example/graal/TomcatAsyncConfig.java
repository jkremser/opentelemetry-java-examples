package io.opentelemetry.example.graal;

import org.springframework.boot.web.embedded.tomcat.TomcatConnectorCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class TomcatAsyncConfig {

    @Bean
    public TomcatConnectorCustomizer asyncTimeoutCustomize() {
        return connector -> {
            // Unlimited timeout for CompletableFutures returned from RestControllers
            connector.setAsyncTimeout(0);
        };
    }
}
