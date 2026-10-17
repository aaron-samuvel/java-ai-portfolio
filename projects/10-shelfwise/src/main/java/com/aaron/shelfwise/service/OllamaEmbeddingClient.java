package com.aaron.shelfwise.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Optional local embedding call. Failures return empty so the caller can use its transparent tag baseline. */
@Component
public class OllamaEmbeddingClient {
    private final String baseUrl;
    private final String model;
    private final int cacheLimit;
    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofMillis(500)).build();
    private final ObjectMapper mapper = new ObjectMapper();
    private final Map<String, List<Double>> cache;

    public OllamaEmbeddingClient(@Value("${shelfwise.ollama.base-url:http://localhost:11434}") String baseUrl,
                                 @Value("${shelfwise.ollama.embedding-model:nomic-embed-text}") String model,
                                 @Value("${shelfwise.ollama.cache-max-entries:1000}") int cacheLimit) {
        this.baseUrl = baseUrl.replaceAll("/+$", ""); this.model = model;
        this.cacheLimit = Math.max(0, Math.min(cacheLimit, 10_000));
        this.cache = new LinkedHashMap<>(16, 0.75f, true);
    }

    public synchronized Optional<List<List<Double>>> embed(List<String> texts) {
        if (texts.isEmpty() || texts.size() > 25 || texts.stream().anyMatch(t -> t == null || t.isBlank() || t.length() > 2000)) return Optional.empty();
        List<String> missing = texts.stream().filter(text -> !cached(text)).distinct().toList();
        if (missing.size() > 25) return Optional.empty();
        Map<String, List<Double>> resolved = new LinkedHashMap<>();
        for (String text : texts) if (cached(text)) resolved.put(text, get(text));
        try {
            if (!missing.isEmpty()) {
                String body = mapper.writeValueAsString(new Request(model, missing));
                var request = HttpRequest.newBuilder(URI.create(baseUrl + "/api/embed")).timeout(Duration.ofSeconds(10))
                        .header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString(body)).build();
                var response = client.send(request, HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() != 200) return Optional.empty();
                JsonNode vectors = mapper.readTree(response.body()).path("embeddings");
                if (!vectors.isArray() || vectors.size() != missing.size()) return Optional.empty();
                List<List<Double>> decoded = new ArrayList<>();
                for (JsonNode vector : vectors) {
                    if (!vector.isArray() || vector.isEmpty()) return Optional.empty();
                    List<Double> values = new ArrayList<>();
                    for (JsonNode value : vector) {
                        double number = value.asDouble(Double.NaN);
                        if (!Double.isFinite(number)) return Optional.empty();
                        values.add(number);
                    }
                    decoded.add(List.copyOf(values));
                }
                if (decoded.stream().map(List::size).distinct().count() != 1) return Optional.empty();
                for (int i = 0; i < missing.size(); i++) {
                    resolved.put(missing.get(i), decoded.get(i));
                    put(missing.get(i), decoded.get(i));
                }
            }
            List<List<Double>> result = texts.stream().map(resolved::get).toList();
            if (result.stream().map(List::size).distinct().count() != 1) return Optional.empty();
            return Optional.of(List.copyOf(result));
        } catch (Exception unavailable) { return Optional.empty(); }
    }

    private boolean cached(String text) { return cacheLimit > 0 && cache.containsKey(text); }
    private List<Double> get(String text) { return cache.get(text); }
    private void put(String text, List<Double> vector) {
        if (cacheLimit == 0) return;
        cache.put(text, vector);
        while (cache.size() > cacheLimit) cache.remove(cache.keySet().iterator().next());
    }

    private record Request(String model, List<String> input) { }
}
