package com.aaron.fraudguard.api;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ScoringLatencyMetricsTest {
    @Test void summarizesOnlyTheMostRecentBoundedSamples() {
        var metrics = new ScoringLatencyMetrics(3);
        metrics.record(10_000);
        metrics.record(20_000);
        metrics.record(30_000);
        metrics.record(40_000);

        var snapshot = metrics.snapshot();
        assertEquals(3, snapshot.sampleCount());
        assertEquals(3, snapshot.windowCapacity());
        assertEquals(30, snapshot.averageMicros());
        assertEquals(30, snapshot.p50Micros());
        assertEquals(40, snapshot.p95Micros());
        assertEquals(40, snapshot.maxMicros());
    }

    @Test void emptyWindowHasZeroCounters() {
        var snapshot = new ScoringLatencyMetrics(2).snapshot();
        assertEquals(0, snapshot.sampleCount());
        assertEquals(0, snapshot.averageMicros());
        assertEquals(0, snapshot.p95Micros());
    }
}
