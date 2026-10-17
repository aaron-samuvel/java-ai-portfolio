package com.aaron.evalops.api;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.LongAdder;
import java.util.function.Predicate;

@RestController
@RequestMapping("/api/evaluations")
public class EvaluationController {
    private final MeterRegistry meterRegistry;
    private final ObservationRegistry observationRegistry;
    private final LongAdder runs = new LongAdder();
    private final LongAdder passedRuns = new LongAdder();
    private final LongAdder totalLatencyMicros = new LongAdder();

    public EvaluationController() {
        this(new io.micrometer.core.instrument.simple.SimpleMeterRegistry(), ObservationRegistry.create());
    }

    @Autowired
    public EvaluationController(MeterRegistry meterRegistry, ObservationRegistry observationRegistry) {
        this.meterRegistry = meterRegistry;
        this.observationRegistry = observationRegistry;
    }

    @PostMapping("/run")
    public Evaluation run() {
        Observation observation = Observation.start("evalops.evaluation.run", observationRegistry);
        long started = System.nanoTime();
        try {
            List<CaseResult> cases = List.of(
                    check("answer_has_citation", "The answer includes source [S1].", text -> text.contains("[S1]")),
                    check("unknown_answer_is_cautious", "I could not find this in the sources.", text -> text.toLowerCase(Locale.ROOT).contains("could not find")),
                    check("no_secret_in_output", "Sample output contains no credential.", text -> !text.toLowerCase(Locale.ROOT).contains("api_key=")));
            long latencyMicros = (System.nanoTime() - started) / 1_000;
            runs.increment();
            totalLatencyMicros.add(latencyMicros);
            boolean allPassed = cases.stream().allMatch(CaseResult::passed);
            if (allPassed) passedRuns.increment();
            String outcome = allPassed ? "passed" : "failed";
            observation.lowCardinalityKeyValue("outcome", outcome);
            Counter.builder("evalops.evaluation.runs").tag("outcome", outcome).register(meterRegistry).increment();
            Timer.builder("evalops.evaluation.duration").tag("outcome", outcome).register(meterRegistry)
                    .record(latencyMicros, java.util.concurrent.TimeUnit.MICROSECONDS);
            return new Evaluation(Instant.now(), cases, cases.stream().filter(CaseResult::passed).count(), cases.size(), latencyMicros,
                    "Synthetic deterministic checks; no prompt or user data is recorded.");
        } finally {
            observation.stop();
        }
    }

    @GetMapping("/metrics")
    public Metrics metrics() {
        long count = runs.sum();
        return new Metrics(count, passedRuns.sum(), count == 0 ? 0 : totalLatencyMicros.sum() / count);
    }

    private CaseResult check(String name, String sample, Predicate<String> rule) {
        boolean ok = rule.test(sample);
        return new CaseResult(name, ok, ok ? "pass" : "fail");
    }

    public record CaseResult(String name, boolean passed, String detail) { }
    public record Evaluation(Instant completedAt, List<CaseResult> cases, long passed, long total,
                             long latencyMicros, String notice) { }
    public record Metrics(long evaluationRuns, long allCasesPassedRuns, long averageLatencyMicros) { }
}
