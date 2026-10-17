package com.aaron.evalops.api;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class QualityGateTest {
    @Test
    void comparesPassRateToConfiguredMinimumAndRequiresAtLeastOneCase() {
        var boundary = QualityGate.evaluate(2, 3, 2.0 / 3.0);
        assertEquals(2.0 / 3.0, boundary.passRate());
        assertTrue(boundary.qualityGatePassed());

        assertFalse(QualityGate.evaluate(1, 3, 0.67).qualityGatePassed());
        var empty = QualityGate.evaluate(0, 0, 0.0);
        assertEquals(0.0, empty.passRate());
        assertFalse(empty.qualityGatePassed());
    }

    @Test
    void rejectsInvalidThresholdsAndCounts() {
        assertThrows(IllegalArgumentException.class, () -> QualityGate.validateMinimum(Double.NaN));
        assertThrows(IllegalArgumentException.class, () -> QualityGate.validateMinimum(1.01));
        assertThrows(IllegalArgumentException.class, () -> QualityGate.evaluate(2, 1, 0.5));
    }
}
