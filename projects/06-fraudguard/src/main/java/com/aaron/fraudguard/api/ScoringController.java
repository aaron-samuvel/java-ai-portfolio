package com.aaron.fraudguard.api;

import com.aaron.fraudguard.model.SyntheticLogisticModel;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/score")
public class ScoringController {
    private final SyntheticLogisticModel model;
    private final FraudReviewStore reviews;
    private final ScoringLatencyMetrics latencyMetrics;
    public ScoringController(SyntheticLogisticModel model, FraudReviewStore reviews, ScoringLatencyMetrics latencyMetrics) {
        this.model = model;
        this.reviews = reviews;
        this.latencyMetrics = latencyMetrics;
    }

    @GetMapping("/metrics")
    public ScoringLatencyMetrics.Snapshot metrics() { return latencyMetrics.snapshot(); }

    @PostMapping
    public Score score(@Valid @RequestBody Transaction tx) {
        long started = System.nanoTime();
        var prediction = model.predict(tx.amount(), tx.recentTransactions(), tx.accountAgeDays(), tx.countryMismatch());
        Map<String, Double> contributions = new LinkedHashMap<>();
        contributions.put("amount", prediction.amountContribution());
        contributions.put("country_mismatch", prediction.countryContribution());
        contributions.put("recent_velocity", prediction.velocityContribution());
        contributions.put("new_account", prediction.accountAgeContribution());
        String band = prediction.probability() >= 0.7 ? "HIGH" : prediction.probability() >= 0.3 ? "REVIEW" : "LOW";
        int riskScore = (int) Math.round(prediction.probability() * 100);
        long decisionId = reviews.record(tx, riskScore, band);
        long scoringLatencyNanos = System.nanoTime() - started;
        latencyMetrics.record(scoringLatencyNanos);
        return new Score(decisionId, riskScore, band, contributions, prediction.bias(), scoringLatencyNanos / 1_000.0,
                prediction.evaluation(), "Synthetic logistic regression trained on deterministic generated transactions only. Educational output; never use for real financial decisions.");
    }

    public record Transaction(@DecimalMin("0.01") @DecimalMax("1000000") double amount,
                             @Min(0) @Max(1000) int recentTransactions,
                             @Min(0) @Max(36500) int accountAgeDays, boolean countryMismatch) { }
    public record Score(long decisionId, long riskScore, String band, Map<String, Double> logitFeatureContributions,
                        double bias, double scoringLatencyMicros, SyntheticLogisticModel.Evaluation evaluation, String notice) { }
}
