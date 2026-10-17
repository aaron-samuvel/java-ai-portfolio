package com.aaron.reviewpilot.queue;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

@Repository
public class ReviewQueue {
    private static final int MAX_PENDING = 1_000;
    private final JdbcTemplate jdbc;

    public ReviewQueue(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public synchronized boolean enqueue(String deliveryId, String repository, int pullRequest, String action) {
        Integer existing = jdbc.queryForObject("select count(*) from review_delivery where delivery_id = ?", Integer.class, deliveryId);
        if (existing != null && existing > 0) return false;
        Long pending = jdbc.queryForObject("select count(*) from review_delivery where status = 'PENDING'", Long.class);
        if (pending != null && pending >= MAX_PENDING) throw new QueueFullException();
        try {
            jdbc.update("insert into review_delivery(delivery_id, repository_name, pull_request_number, action) values (?, ?, ?, ?)",
                    deliveryId, repository, pullRequest, action);
            return true;
        } catch (DuplicateKeyException duplicate) {
            return false;
        }
    }

    public boolean complete(String deliveryId) {
        return jdbc.update("update review_delivery set status = 'COMPLETED' where delivery_id = ? and status = 'PENDING'", deliveryId) == 1;
    }

    public List<ReviewDelivery> pending(int requestedLimit) {
        int limit = Math.max(1, Math.min(requestedLimit, 100));
        return jdbc.query("select delivery_id, repository_name, pull_request_number, action, received_at, status from review_delivery where status = 'PENDING' order by received_at desc limit ?",
                (rs, row) -> new ReviewDelivery(rs.getString("delivery_id"), rs.getString("repository_name"),
                        rs.getInt("pull_request_number"), rs.getString("action"), rs.getTimestamp("received_at").toInstant(), rs.getString("status")),
                limit);
    }

    public record ReviewDelivery(String deliveryId, String repository, int pullRequest, String action,
                                 Instant receivedAt, String status) { }

    public static class QueueFullException extends RuntimeException { }
}
