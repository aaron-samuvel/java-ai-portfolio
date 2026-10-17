package com.aaron.evalops.api;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DocuMindEvidenceMetricsTest {
    @Test
    void computesBestSourceEvidenceRecallWithoutRetainingText() {
        double recall = DocuMindEvidenceMetrics.bestEvidenceRecall("failed records twice raises alert",
                List.of("unrelated source", "Batch processing retries failed records twice, then raises an alert."));
        assertEquals(1.0, recall);
    }

    @Test
    void returnsZeroForMissingSourceEvidence() {
        assertEquals(0.0, DocuMindEvidenceMetrics.bestEvidenceRecall("audit logs retained days", List.of("No matching policy.")));
        assertEquals(0.0, DocuMindEvidenceMetrics.bestEvidenceRecall("", List.of("some content")));
    }
}
