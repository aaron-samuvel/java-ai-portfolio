package com.aaron.visioncheck.service;

import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Stores only classifier metadata and human labels; uploaded image bytes are never retained. */
@Service
public class ReviewQueue {
    private static final int MAX_RECORDS = 1000;
    private final Map<String, ReviewItem> items = new ConcurrentHashMap<>();

    public ReviewItem enqueue(String predictedLabel, int width, int height) {
        if (items.size() >= MAX_RECORDS) throw new IllegalStateException("Local review queue is full; restart the demo to clear it");
        String id = UUID.randomUUID().toString();
        ReviewItem item = new ReviewItem(id, predictedLabel, null, width, height, "PENDING", Instant.now());
        items.put(id, item);
        return item;
    }

    public List<ReviewItem> pending() {
        return items.values().stream().filter(item -> item.status().equals("PENDING"))
                .sorted(Comparator.comparing(ReviewItem::createdAt)).toList();
    }

    public ReviewItem correct(String id, String label) {
        return items.compute(id, (key, current) -> {
            if (current == null) throw new ReviewNotFoundException();
            if (!current.status().equals("PENDING")) throw new ReviewAlreadyCompletedException();
            return new ReviewItem(id, current.predictedLabel(), label, current.width(), current.height(), "REVIEWED", current.createdAt());
        });
    }

    public static class ReviewNotFoundException extends RuntimeException { }
    public static class ReviewAlreadyCompletedException extends RuntimeException { }
    public record ReviewItem(String id, String predictedLabel, String correctedLabel, int width, int height,
                             String status, Instant createdAt) { }
}
