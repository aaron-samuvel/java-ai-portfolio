package com.aaron.fraudguard.api;

import org.springframework.stereotype.Component;

import java.util.Arrays;

/** Bounded in-memory latency window; it stores durations only, never transaction fields. */
@Component
public class ScoringLatencyMetrics {
    private final long[] samples;
    private int next;
    private int size;

    public ScoringLatencyMetrics() {
        this(512);
    }

    ScoringLatencyMetrics(int capacity) {
        if (capacity < 1) throw new IllegalArgumentException("capacity must be positive");
        samples = new long[capacity];
    }

    public synchronized void record(long elapsedNanos) {
        if (elapsedNanos < 0) throw new IllegalArgumentException("latency cannot be negative");
        samples[next] = elapsedNanos;
        next = (next + 1) % samples.length;
        size = Math.min(size + 1, samples.length);
    }

    public synchronized Snapshot snapshot() {
        if (size == 0) return new Snapshot(0, samples.length, 0, 0, 0, 0);
        long[] ordered = new long[size];
        System.arraycopy(samples, 0, ordered, 0, size);
        Arrays.sort(ordered);
        long total = 0;
        for (long sample : ordered) total += sample;
        return new Snapshot(size, samples.length, total / (double) size / 1_000,
                percentileMicros(ordered, 0.50), percentileMicros(ordered, 0.95),
                ordered[size - 1] / 1_000.0);
    }

    private double percentileMicros(long[] sorted, double percentile) {
        int index = Math.max(0, (int) Math.ceil(percentile * sorted.length) - 1);
        return sorted[index] / 1_000.0;
    }

    public record Snapshot(int sampleCount, int windowCapacity, double averageMicros,
                           double p50Micros, double p95Micros, double maxMicros) { }
}
