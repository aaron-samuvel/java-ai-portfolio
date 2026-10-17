package com.aaron.documind.ingestion;

import org.springframework.ai.document.Document;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

final class ChunkOverlap {
    private ChunkOverlap() { }

    static List<Document> apply(List<Document> chunks, int overlapWords) {
        if (overlapWords < 0) throw new IllegalArgumentException("Overlap cannot be negative.");
        if (overlapWords == 0 || chunks.size() < 2) return chunks;

        List<Document> result = new ArrayList<>(chunks.size());
        result.add(chunks.getFirst());
        for (int i = 1; i < chunks.size(); i++) {
            String previous = chunks.get(i - 1).getText();
            String current = chunks.get(i).getText();
            String[] words = previous.strip().split("\\s+");
            int start = Math.max(0, words.length - overlapWords);
            String prefix = String.join(" ", java.util.Arrays.copyOfRange(words, start, words.length));
            HashMap<String, Object> metadata = new HashMap<>(chunks.get(i).getMetadata());
            result.add(new Document(prefix + " " + current, metadata));
        }
        return result;
    }
}
