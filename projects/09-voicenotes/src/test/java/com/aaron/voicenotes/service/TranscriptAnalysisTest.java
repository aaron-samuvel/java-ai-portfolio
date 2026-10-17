package com.aaron.voicenotes.service;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TranscriptAnalysisTest {
    @Test void returnsExtractiveSummaryAndCandidateActionsWithoutInventingText() {
        String transcript = "We reviewed the launch. We decided to delay the release. Alex will prepare the checklist. The meeting ended.";
        var result = new TranscriptAnalysis().analyze(transcript);
        assertEquals("We reviewed the launch. We decided to delay the release. Alex will prepare the checklist.", result.summary());
        assertEquals(1, result.decisions().size());
        assertEquals(1, result.actions().size());
        assertTrue(result.summary().contains(result.actions().getFirst()));
        assertEquals("EXTRACTIVE_RULES", result.method());
    }
}
