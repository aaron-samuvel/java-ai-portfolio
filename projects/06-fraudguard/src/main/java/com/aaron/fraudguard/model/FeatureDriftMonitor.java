package com.aaron.fraudguard.model;

import java.util.ArrayList;
import java.util.List;

/** Population-stability comparison for bounded, normalized numeric features. */
public final class FeatureDriftMonitor {
    private static final double SMOOTHING = 1e-6;

    public Report compare(List<double[]> reference, List<double[]> current, List<String> featureNames,
                          int bins, double threshold) {
        if (reference == null || current == null || featureNames == null || reference.isEmpty() || current.isEmpty()) {
            throw new IllegalArgumentException("Both reference and current samples must be non-empty");
        }
        if (bins < 2 || !Double.isFinite(threshold) || threshold <= 0) {
            throw new IllegalArgumentException("bins must be at least 2 and threshold must be positive");
        }
        int dimensions = featureNames.size();
        if (dimensions == 0) throw new IllegalArgumentException("At least one feature name is required");
        int[][] referenceCounts = counts(reference, dimensions, bins);
        int[][] currentCounts = counts(current, dimensions, bins);
        List<Feature> features = new ArrayList<>(dimensions);
        double maxPsi = 0;
        for (int feature = 0; feature < dimensions; feature++) {
            double psi = 0;
            for (int bin = 0; bin < bins; bin++) {
                double expected = (referenceCounts[feature][bin] + SMOOTHING)
                        / (reference.size() + SMOOTHING * bins);
                double actual = (currentCounts[feature][bin] + SMOOTHING)
                        / (current.size() + SMOOTHING * bins);
                psi += (actual - expected) * Math.log(actual / expected);
            }
            maxPsi = Math.max(maxPsi, psi);
            features.add(new Feature(featureNames.get(feature), psi, psi >= threshold));
        }
        return new Report(features, maxPsi, threshold, maxPsi >= threshold);
    }

    private int[][] counts(List<double[]> rows, int dimensions, int bins) {
        int[][] counts = new int[dimensions][bins];
        for (double[] row : rows) {
            if (row == null || row.length != dimensions) {
                throw new IllegalArgumentException("Every sample must have exactly " + dimensions + " features");
            }
            for (int feature = 0; feature < dimensions; feature++) {
                double value = row[feature];
                if (!Double.isFinite(value) || value < 0 || value > 1) {
                    throw new IllegalArgumentException("Features must be finite and normalized to [0, 1]");
                }
                int bin = Math.min(bins - 1, (int) (value * bins));
                counts[feature][bin]++;
            }
        }
        return counts;
    }

    public record Feature(String name, double populationStabilityIndex, boolean driftDetected) { }
    public record Report(List<Feature> features, double maximumPopulationStabilityIndex,
                         double threshold, boolean driftDetected) { }
}
