package com.aaron.documind.ingestion;

import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ChunkOverlapTest {
    @Test
    void prefixesEachChunkWithBoundedWordsFromItsPredecessorAndPreservesMetadata() {
        var first = new Document("alpha beta gamma", Map.of("page", 1));
        var second = new Document("delta epsilon", Map.of("page", 2));

        List<Document> chunks = ChunkOverlap.apply(List.of(first, second), 2);

        assertEquals("alpha beta gamma", chunks.get(0).getText());
        assertEquals("beta gamma delta epsilon", chunks.get(1).getText());
        assertEquals(2, chunks.get(1).getMetadata().get("page"));
    }

    @Test
    void zeroOverlapLeavesInputUntouchedAndNegativeOverlapIsRejected() {
        var chunk = new Document("one two");
        assertEquals(List.of(chunk), ChunkOverlap.apply(List.of(chunk), 0));
        assertThrows(IllegalArgumentException.class, () -> ChunkOverlap.apply(List.of(chunk), -1));
    }
}
