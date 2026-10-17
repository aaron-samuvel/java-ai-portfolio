package com.aaron.visioncheck.service;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ReviewQueueTest {
    @Test void recordsHumanCorrectionWithoutRetainingImageData() {
        var queue = new ReviewQueue();
        var pending = queue.enqueue("blue-dominant scene", 100, 80);
        var reviewed = queue.correct(pending.id(), "ocean");
        assertEquals("REVIEWED", reviewed.status());
        assertEquals("ocean", reviewed.correctedLabel());
        assertTrue(queue.pending().isEmpty());
        assertThrows(ReviewQueue.ReviewAlreadyCompletedException.class, () -> queue.correct(pending.id(), "sky"));
    }
}
