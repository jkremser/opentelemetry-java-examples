package io.opentelemetry.example.graal;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

@RestController
public class Controller {
    private final List<CompletableFuture<String>> clients = new CopyOnWriteArrayList<>();

    @GetMapping("/ping")
    public String ping() {
        return "pong";
    }


    @GetMapping("/longpolling/{deadline}")
    public CompletableFuture<String> longPolling(@PathVariable int deadline) {
        // Complete the task in random time
        double chanceToExpire = 0.15;
        int seconds = ThreadLocalRandom.current().nextInt(5, (int) (deadline * (1.0 + chanceToExpire)));
        CompletableFuture<String> future = new CompletableFuture<>();

        if (seconds < deadline) {
            CompletableFuture.delayedExecutor(seconds, TimeUnit.SECONDS).execute(() -> {
                if (!future.isDone()) {
                    future.complete("Your task is done, it took " + seconds + "s");
                }
                clients.remove(future);
            });
        } else {
            // Timeout after deadline seconds if no update is received
            CompletableFuture.delayedExecutor(deadline, TimeUnit.SECONDS).execute(() -> {
                if (!future.isDone()) {
                    future.complete("Task has expired (" + deadline + " seconds has elapsed)");
                }
                clients.remove(future);
            });
        }
        clients.add(future);
        System.out.println("Task has been started, expected time to finish: " + seconds + "s. Number of running tasks: " + clients.size());

        return future;
    }
}
