package com.aaron.fraudguard.model;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class SyntheticLogisticModelTest {
    @Test void trainsDeterministicallyAndReturnsBoundedScores() {
        var model = new SyntheticLogisticModel();
        var first = model.predict(800, 4, 20, true);
        var second = model.predict(800, 4, 20, true);
        assertEquals(first.probability(), second.probability(), 1e-12);
        assertTrue(first.probability() >= 0 && first.probability() <= 1);
        assertEquals(200_000, first.evaluation().generatedTransactions());
        assertEquals(160_000, first.evaluation().trainingTransactions());
        assertEquals(40_000, first.evaluation().holdoutTransactions());
        assertTrue(first.evaluation().generatedPositiveRate() < 0.2);
        assertTrue(first.evaluation().holdoutAveragePrecision() >= 0 && first.evaluation().holdoutAveragePrecision() <= 1);
        assertTrue(first.evaluation().holdoutExpectedCalibrationError() >= 0 && first.evaluation().holdoutExpectedCalibrationError() <= 1);
        var extreme = model.predict(1_000_000, 1_000, 0, true);
        assertTrue(extreme.probability() >= 0.3, () -> "extreme synthetic feature combination should enter the analyst review band; actual=" + extreme);
        assertTrue(first.evaluation().holdoutAveragePrecision() > first.evaluation().generatedPositiveRate(),
                "holdout ranking should beat the positive-rate baseline on this generated fixture");
        assertEquals(4, first.evaluation().featureDrift().features().size());
        assertFalse(first.evaluation().featureDrift().driftDetected(),
                "the generated train and holdout feature distributions are intentionally stable");
        assertTrue(first.evaluation().featureDrift().maximumPopulationStabilityIndex() >= 0);
    }
}
