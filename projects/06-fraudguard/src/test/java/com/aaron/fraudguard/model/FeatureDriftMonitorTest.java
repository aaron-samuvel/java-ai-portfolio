package com.aaron.fraudguard.model;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class FeatureDriftMonitorTest {
    private final FeatureDriftMonitor monitor = new FeatureDriftMonitor();

    @Test void detectsAShiftAndNamesTheAffectedFeature() {
        var reference = List.of(new double[]{0.1, 0.2}, new double[]{0.2, 0.2}, new double[]{0.1, 0.2});
        var current = List.of(new double[]{0.9, 0.2}, new double[]{0.8, 0.2}, new double[]{0.9, 0.2});

        var report = monitor.compare(reference, current, List.of("amount", "velocity"), 10, 0.2);

        assertTrue(report.driftDetected());
        assertTrue(report.features().get(0).driftDetected());
        assertFalse(report.features().get(1).driftDetected());
        assertTrue(report.features().get(0).populationStabilityIndex() > 0.2);
        assertEquals("amount", report.features().get(0).name());
    }

    @Test void rejectsEmptyOrOutOfRangeSamples() {
        assertThrows(IllegalArgumentException.class,
                () -> monitor.compare(List.of(), List.of(new double[]{0.2}), List.of("amount"), 10, 0.2));
        assertThrows(IllegalArgumentException.class,
                () -> monitor.compare(List.of(new double[]{1.1}), List.of(new double[]{0.2}), List.of("amount"), 10, 0.2));
    }
}
