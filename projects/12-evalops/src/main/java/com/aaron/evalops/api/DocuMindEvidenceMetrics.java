package com.aaron.evalops.api;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

final class DocuMindEvidenceMetrics {
    private DocuMindEvidenceMetrics() { }

    static double bestEvidenceRecall(String expectedEvidence, List<String> sourceSnippets) {
        Set<String> expected = terms(expectedEvidence);
        if (expected.isEmpty() || sourceSnippets == null || sourceSnippets.isEmpty()) return 0;
        return sourceSnippets.stream().mapToDouble(snippet -> {
            Set<String> actual = terms(snippet);
            long matched = expected.stream().filter(actual::contains).count();
            return (double) matched / expected.size();
        }).max().orElse(0);
    }

    private static Set<String> terms(String text) {
        if (text == null) return Set.of();
        return Arrays.stream(text.toLowerCase(Locale.ROOT).split("[^\\p{Alnum}]+"))
                .filter(term -> term.length() > 2).collect(Collectors.toSet());
    }
}
