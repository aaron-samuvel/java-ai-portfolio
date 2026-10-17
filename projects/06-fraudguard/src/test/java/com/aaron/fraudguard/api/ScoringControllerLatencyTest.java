package com.aaron.fraudguard.api;

import com.aaron.fraudguard.model.SyntheticLogisticModel;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

class ScoringControllerLatencyTest {
    @Test void scoringReturnsLatencyAndRecordsAggregateSample() {
        var metrics = new ScoringLatencyMetrics(8);
        var controller = new ScoringController(new SyntheticLogisticModel(), mock(FraudReviewStore.class), metrics);

        var score = controller.score(new ScoringController.Transaction(420, 7, 12, true));

        assertTrue(score.scoringLatencyMicros() >= 0);
        assertEquals(1, controller.metrics().sampleCount());
        assertTrue(controller.metrics().averageMicros() >= 0);
    }
}
