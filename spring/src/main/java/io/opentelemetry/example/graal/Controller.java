package io.opentelemetry.example.graal;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.*;

@RestController
public class Controller {
    private final Map<String, CompletableFuture<String>> clients = new ConcurrentHashMap<>();

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
        String taskId = UUID.randomUUID().toString().substring(0, 5);

        if (seconds < deadline) {
            CompletableFuture.delayedExecutor(seconds, TimeUnit.SECONDS).execute(() -> {
                if (!future.isDone()) {
                    future.complete("Your task '" + taskId + "' is done, it took " + seconds + "s");
                }
                clients.remove(taskId);
            });
        } else {
            // Timeout after deadline seconds if no update is received
            CompletableFuture.delayedExecutor(deadline, TimeUnit.SECONDS).execute(() -> {
                if (!future.isDone()) {
                    future.complete("Task '" + taskId + "' has expired (" + deadline + " seconds has elapsed)");
                }
                clients.remove(taskId);
            });
        }
        clients.put(taskId, future);
        System.out.println("Task '" + taskId + "' has been started, expected time to finish: " + seconds + "s. Number of running tasks: " + clients.size());

        return future;
    }
}
