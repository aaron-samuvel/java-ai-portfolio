package com.aaron.evalops.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;

class DocuMindGoldenEvaluationControllerTest {
    @Test
    void acceptsOnlyAnHttpLoopbackOriginForTheDocuMindAdapter() {
        new DocuMindGoldenEvaluationController(new ObjectMapper(), "http://127.0.0.1:18081");
        new DocuMindGoldenEvaluationController(new ObjectMapper(), "http://localhost:8081");
        assertThrows(IllegalArgumentException.class,
                () -> new DocuMindGoldenEvaluationController(new ObjectMapper(), "http://example.com:8081"));
        assertThrows(IllegalArgumentException.class,
                () -> new DocuMindGoldenEvaluationController(new ObjectMapper(), "http://127.0.0.1:8081/path"));
    }
}
