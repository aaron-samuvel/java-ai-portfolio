package com.aaron.evalops.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/** Reads only loopback Actuator health status from portfolio applications. */
@RestController
@RequestMapping("/api/portfolio")
public class PortfolioHealthController {
    private static final List<ServiceTarget> TARGETS = List.of(
            new ServiceTarget("01-documind", 8081), new ServiceTarget("02-helpdesk-copilot", 8082),
            new ServiceTarget("03-toolbridge", 8083), new ServiceTarget("04-research-crew", 8084),
            new ServiceTarget("05-reviewpilot", 8085), new ServiceTarget("06-fraudguard", 8086),
            new ServiceTarget("07-visioncheck", 8087), new ServiceTarget("08-askyourdata", 8088),
            new ServiceTarget("09-voicenotes", 8089), new ServiceTarget("10-shelfwise", 8090),
            new ServiceTarget("11-papertrail", 8091), new ServiceTarget("12-evalops", 8092));

    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofMillis(500)).build();
    private final ObjectMapper mapper;

    public PortfolioHealthController(ObjectMapper mapper) { this.mapper = mapper; }

    @GetMapping("/health")
    public PortfolioHealth health() {
        List<CompletableFuture<ServiceHealth>> checks = TARGETS.stream().map(target ->
                client.sendAsync(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + target.port() + "/actuator/health"))
                                .timeout(Duration.ofSeconds(2)).GET().build(), HttpResponse.BodyHandlers.ofString())
                        .thenApply(response -> readStatus(target, response.statusCode(), response.body()))
                        .exceptionally(error -> new ServiceHealth(target.name(), "DOWN")))
                .toList();
        CompletableFuture.allOf(checks.toArray(CompletableFuture[]::new)).join();
        List<ServiceHealth> services = checks.stream().map(CompletableFuture::join)
                .sorted(Comparator.comparing(ServiceHealth::name)).toList();
        long up = services.stream().filter(service -> service.status().equals("UP")).count();
        return new PortfolioHealth(up, services.size(), services);
    }

    private ServiceHealth readStatus(ServiceTarget target, int code, String body) {
        if (code != 200) return new ServiceHealth(target.name(), "DOWN");
        try {
            String status = mapper.readTree(body).path("status").asText("UNKNOWN");
            return new ServiceHealth(target.name(), status.equals("UP") ? "UP" : "DOWN");
        } catch (Exception ignored) {
            return new ServiceHealth(target.name(), "DOWN");
        }
    }

    private record ServiceTarget(String name, int port) { }
    public record ServiceHealth(String name, String status) { }
    public record PortfolioHealth(long up, long total, List<ServiceHealth> services) { }
}
