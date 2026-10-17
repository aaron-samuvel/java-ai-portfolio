package com.aaron.shelfwise.api;

import com.aaron.shelfwise.service.OllamaEmbeddingClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class RecommendationControllerTest {
    @Test void recentInteractionsInfluenceTheProfileMoreThanOldEvents() {
        var embeddings = new OllamaEmbeddingClient("http://127.0.0.1:1", "none", 0);
        var controller = new RecommendationController(embeddings);
        var recent = new RecommendationController.Request(List.of("BK2"), List.of(
                new RecommendationController.Interaction("BK1", RecommendationController.InteractionType.VIEW, Instant.now())));
        var old = new RecommendationController.Request(List.of("BK2"), List.of(
                new RecommendationController.Interaction("BK1", RecommendationController.InteractionType.VIEW, Instant.now().minusSeconds(180L * 86_400))));

        var recentBook = controller.recommend(recent).stream().filter(r -> r.itemId().equals("BK3")).findFirst().orElseThrow();
        var decayedBook = controller.recommend(old).stream().filter(r -> r.itemId().equals("BK3")).findFirst().orElseThrow();

        assertTrue(recentBook.contentSimilarity() > decayedBook.contentSimilarity());
        assertTrue(recentBook.collaborativeSignal() > decayedBook.collaborativeSignal());
    }

    @Test void excludesLikedItemsAndReturnsAValidSyntheticRankingMetric() {
        var controller = new RecommendationController();
        var results = controller.recommend(new RecommendationController.Request(List.of("BK1")));
        assertTrue(results.stream().noneMatch(r -> r.itemId().equals("BK1")));
        assertTrue(results.stream().allMatch(r -> r.score() >= 0 && r.baseRelevance() >= r.diversityPenalty()));
        assertTrue(results.stream().anyMatch(r -> r.explanation().contains("diversity penalty")));
        assertTrue(results.stream().allMatch(r -> r.contentMethod().equals("TAG_COSINE_FALLBACK") || r.contentMethod().equals("OLLAMA_EMBEDDING")));
        var evaluation = controller.evaluateSample();
        assertEquals("NDCG@3", evaluation.metric());
        assertTrue(evaluation.value() >= 0 && evaluation.value() <= 1);
    }

    @Test void reusesCachedCatalogueVectorsAcrossRequests() throws Exception {
        AtomicInteger requests = new AtomicInteger();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/embed", exchange -> {
            requests.incrementAndGet();
            int count = new ObjectMapper().readTree(exchange.getRequestBody()).path("input").size();
            String vectors = java.util.stream.IntStream.range(0, count).mapToObj(i -> "[1.0,0.0]")
                    .collect(java.util.stream.Collectors.joining(","));
            byte[] body = ("{\"embeddings\":[" + vectors + "]}").getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
        try {
            var client = new OllamaEmbeddingClient("http://127.0.0.1:" + server.getAddress().getPort(), "test-model", 10);
            var first = client.embed(List.of("catalogue item", "user profile"));
            var second = client.embed(List.of("catalogue item", "user profile"));
            assertTrue(first.isPresent());
            assertEquals(first, second);
            assertEquals(1, requests.get(), "the second lookup should be served from the local cache");

            var smallerThanBatchCache = new OllamaEmbeddingClient("http://127.0.0.1:" + server.getAddress().getPort(), "test-model", 1);
            assertEquals(2, smallerThanBatchCache.embed(List.of("first", "second")).orElseThrow().size());
            var uncached = new OllamaEmbeddingClient("http://127.0.0.1:" + server.getAddress().getPort(), "test-model", 0);
            assertTrue(uncached.embed(List.of("one")).isPresent());
            assertTrue(uncached.embed(List.of("one")).isPresent());
            assertEquals(4, requests.get());
        } finally {
            server.stop(0);
        }
    }

    @Test void evictsLeastRecentlyUsedVectorWhenCacheIsFull() throws Exception {
        AtomicInteger requests = new AtomicInteger();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/embed", exchange -> {
            requests.incrementAndGet();
            byte[] body = "{\"embeddings\":[[1.0,0.0]]}".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
        try {
            var client = new OllamaEmbeddingClient("http://127.0.0.1:" + server.getAddress().getPort(), "test-model", 1);
            assertTrue(client.embed(List.of("a")).isPresent());
            assertTrue(client.embed(List.of("b")).isPresent());
            assertTrue(client.embed(List.of("a")).isPresent());
            assertEquals(3, requests.get());
        } finally {
            server.stop(0);
        }
    }
}
