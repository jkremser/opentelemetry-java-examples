package io.opentelemetry.example.graal;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.util.DefaultUriBuilderFactory;

import java.io.IOException;
import java.util.Optional;

@SpringBootApplication
public class Application {
    public static final int VERSION = 1;

    public static void main(String[] args) throws IOException {
        System.out.println("version=" + VERSION);
        Optional<String> maybeServer = Optional.ofNullable(System.getenv("SERVER"));
        boolean longPolling = Optional.of("true".equals(System.getenv("LONG_POLLING"))).orElse(false);
        String sleepMs = Optional.ofNullable(System.getenv("SLEEP_MS")).orElseThrow(() -> new IOException("SLEEP_MS is not set in the environment"));
        System.out.println("SERVER=" + maybeServer);
        System.out.println("SLEEP_MS=" + sleepMs);
        String deadlineStr = Optional.ofNullable(System.getenv("DEADLINE")).orElse("300");
        System.out.println("DEADLINE=" + deadlineStr);
        int sleepMsInt;
        try {
            sleepMsInt = Integer.parseInt(sleepMs);
        } catch (NumberFormatException e) {
            System.out.println("Unable to parse SLEEP_MS, defaulting to 5000ms");
            sleepMsInt = 5000;
        }
        int deadline = 0;
        try {
            deadline = Integer.parseInt(deadlineStr);
        } catch (NumberFormatException e) {
            System.out.println("Unable to parse DEADLINE");
            System.exit(1);
        }
        final int sleepMsIntFinal = sleepMsInt;
        final int deadlineFinal = deadline;
        ApplicationContext applicationContext = SpringApplication.run(Application.class, args);
        System.out.println("\nListening for requests on localhost:8080");
        System.out.println(" -   /ping");
        System.out.println(" -   /longpolling/{deadline}");
        maybeServer.ifPresent(server -> {
            if (server.trim().isEmpty()) {
                return;
            }
            Pinger pinger = applicationContext.getBean(Pinger.class);
            System.out.println("Running the pinger, it will be sending requests to " + server);
            // required in order to be able to set the Host HTTP header
            System.setProperty("sun.net.http.allowRestrictedHeaders", "true");

            while (true) {
                System.out.println("\n> Making " + (longPolling ? "(long-polling) " : "") + "HTTP call..");
                try {
                    if (longPolling) {
                        // this is an async call that registers the listener for the completable future
                        pinger.longpolling(deadlineFinal).thenAccept(res -> System.out.println("< result: " + res));
                    } else {
                        // this call blocks and wait for the result
                        System.out.println("< result: " + pinger.ping());
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                } finally {
                    try {
                        if (sleepMsIntFinal > 0) {
                            Thread.sleep(sleepMsIntFinal);
                        }
                    } catch (InterruptedException e) {
                        System.out.println(e.getMessage());
                    }
                }
            }
        });
    }
}
