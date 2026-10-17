package com.aaron.reviewpilot.queue;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.JdbcTest;

import static org.junit.jupiter.api.Assertions.*;

@JdbcTest
class ReviewQueueTest {
    @Autowired org.springframework.jdbc.core.JdbcTemplate jdbc;

    @Test
    void persistsOnlyReviewMetadataAndRetainsReplayProtectionAfterCompletion() {
        var queue = new ReviewQueue(jdbc);
        String id = "a1b2c3d4-0000-4000-8000-000000000001";

        assertTrue(queue.enqueue(id, "aaron/demo", 7, "opened"));
        assertEquals("aaron/demo", queue.pending(50).getFirst().repository());
        assertTrue(queue.complete(id));
        assertTrue(queue.pending(50).isEmpty());
        assertFalse(new ReviewQueue(jdbc).enqueue(id, "aaron/demo", 7, "opened"));
        assertEquals(1, jdbc.queryForObject("select count(*) from review_delivery", Integer.class));
    }

    @Test
    void boundsRequestedQueuePageSize() {
        var queue = new ReviewQueue(jdbc);
        assertTrue(queue.pending(10_000).isEmpty());
        assertTrue(queue.pending(0).isEmpty());
    }
}
