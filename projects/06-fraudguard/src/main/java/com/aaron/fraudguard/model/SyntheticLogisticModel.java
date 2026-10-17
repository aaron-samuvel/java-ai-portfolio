package com.aaron.fraudguard.model;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Random;

/** Reproducible educational model trained on generated transactions only. */
@Component
public class SyntheticLogisticModel {
    private static final int FEATURES = 4;
    private static final int SAMPLE_COUNT = 200_000;
    private static final int TRAINING_DAYS = 48;
    private static final int EPOCHS = 1000;
    private static final double DRIFT_THRESHOLD = 0.2;
    private final double[] weights = new double[FEATURES + 1];
    private final Evaluation evaluation;

    public SyntheticLogisticModel() {
        Random random = new Random(20261017L);
        List<Example> examples = new ArrayList<>(SAMPLE_COUNT);
        long positives = 0;
        for (int i = 0; i < SAMPLE_COUNT; i++) {
            int day = i * 60 / SAMPLE_COUNT;
            double[] x = {random.nextDouble(), random.nextDouble(), random.nextDouble(), random.nextDouble()};
            // The low base rate deliberately makes the generated labels imbalanced.
            double probability = sigmoid(-8.0 + 2.0*x[0] + 3.4*x[1] + 2.2*x[2] + 2.0*x[3]);
            int label = random.nextDouble() < probability ? 1 : 0;
            positives += label;
            examples.add(new Example(x, label, day));
        }

        List<Example> training = examples.stream().filter(e -> e.day() < TRAINING_DAYS).toList();
        List<Example> holdout = examples.stream().filter(e -> e.day() >= TRAINING_DAYS).toList();
        long trainingPositives = training.stream().filter(e -> e.label() == 1).count();
        double trainingRate = (double) trainingPositives / training.size();
        weights[0] = Math.log(trainingRate / (1.0 - trainingRate));
        for (int epoch = 0; epoch < EPOCHS; epoch++) {
            double[] gradient = new double[weights.length];
            for (Example example : training) {
                double error = sigmoid(logit(example.features())) - example.label();
                gradient[0] += error;
                for (int j = 0; j < FEATURES; j++) gradient[j + 1] += error * example.features()[j];
            }
            double rate = 0.8 / training.size();
            for (int j = 0; j < weights.length; j++) weights[j] -= rate * gradient[j];
        }

        double ap = averagePrecision(holdout);
        double ece = expectedCalibrationError(holdout, 10);
        FeatureDriftMonitor.Report drift = new FeatureDriftMonitor().compare(
                training.stream().map(Example::features).toList(),
                holdout.stream().map(Example::features).toList(),
                List.of("amount", "countryMismatch", "transactionVelocity", "accountAge"), 10, DRIFT_THRESHOLD);
        evaluation = new Evaluation("60-day chronological holdout", SAMPLE_COUNT, training.size(), holdout.size(),
                (double) positives / SAMPLE_COUNT, ap, ece, 10, drift);
    }

    public Prediction predict(double amount, int recentTransactions, int accountAgeDays, boolean countryMismatch) {
        double[] x = features(amount, recentTransactions, accountAgeDays, countryMismatch);
        double probability = sigmoid(logit(x));
        return new Prediction(probability, weights[1] * x[0], weights[2] * x[1], weights[3] * x[2],
                weights[4] * x[3], weights[0], evaluation);
    }

    private double[] features(double amount, int velocity, int ageDays, boolean countryMismatch) {
        return new double[]{Math.min(1, Math.log1p(amount) / Math.log1p(5000)), countryMismatch ? 1 : 0,
                Math.min(1, velocity / 10.0), Math.max(0, 30 - ageDays) / 30.0};
    }
    private double logit(double[] x) { double result = weights[0]; for (int j=0;j<FEATURES;j++) result += weights[j+1]*x[j]; return result; }
    private double sigmoid(double value) { return 1.0 / (1.0 + Math.exp(-value)); }

    private double averagePrecision(List<Example> evaluation) {
        List<Example> ranked = evaluation.stream().sorted(Comparator.comparingDouble((Example e) -> sigmoid(logit(e.features()))).reversed()).toList();
        long positives = ranked.stream().filter(e -> e.label() == 1).count();
        if (positives == 0) return 0;
        int seen = 0; double precisionSum = 0;
        for (int i=0;i<ranked.size();i++) if (ranked.get(i).label() == 1) { seen++; precisionSum += (double)seen/(i+1); }
        return precisionSum / positives;
    }

    private double expectedCalibrationError(List<Example> cases, int bins) {
        int[] count = new int[bins]; double[] confidence = new double[bins]; double[] observed = new double[bins];
        for (Example example : cases) {
            double probability = sigmoid(logit(example.features()));
            int bin = Math.min(bins - 1, (int)(probability * bins));
            count[bin]++; confidence[bin] += probability; observed[bin] += example.label();
        }
        double error = 0;
        for (int bin=0;bin<bins;bin++) if (count[bin] > 0) {
            error += (double)count[bin] / cases.size() * Math.abs(confidence[bin] / count[bin] - observed[bin] / count[bin]);
        }
        return error;
    }

    private record Example(double[] features, int label, int day) { }
    public record Evaluation(String split, int generatedTransactions, int trainingTransactions, int holdoutTransactions,
                             double generatedPositiveRate, double holdoutAveragePrecision,
                             double holdoutExpectedCalibrationError, int calibrationBins,
                             FeatureDriftMonitor.Report featureDrift) { }
    public record Prediction(double probability, double amountContribution, double countryContribution,
                             double velocityContribution, double accountAgeContribution, double bias,
                             Evaluation evaluation) { }
}
