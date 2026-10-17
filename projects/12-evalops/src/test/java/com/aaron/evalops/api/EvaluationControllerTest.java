package com.aaron.evalops.api;
import org.junit.jupiter.api.Test;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.micrometer.observation.ObservationRegistry;
import static org.junit.jupiter.api.Assertions.*;
class EvaluationControllerTest {
    @Test void evaluatesSamplesAndAccumulatesOnlyAggregateMetrics() {
        var registry = new SimpleMeterRegistry();
        var controller = new EvaluationController(registry, ObservationRegistry.create());
        assertEquals(0, controller.metrics().evaluationRuns());
        var result = controller.run();
        assertEquals(result.total(), result.passed());
        assertEquals(1, controller.metrics().evaluationRuns());
        assertEquals(1, controller.metrics().allCasesPassedRuns());
        assertEquals(1.0, registry.get("evalops.evaluation.runs").tag("outcome", "passed").counter().count());
        assertEquals(1, registry.get("evalops.evaluation.duration").tag("outcome", "passed").timer().count());
    }
}
