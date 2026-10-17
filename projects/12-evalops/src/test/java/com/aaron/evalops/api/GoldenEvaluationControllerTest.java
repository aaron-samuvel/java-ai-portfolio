package com.aaron.evalops.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class GoldenEvaluationControllerTest {
    private final ObjectMapper mapper = new ObjectMapper();

    @Test void checksVersionedStructuredPlansAndReportResults() throws Exception {
        var requests = new AtomicInteger();
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/analytics", exchange -> {
            requests.incrementAndGet();
            var body = mapper.readTree(exchange.getRequestBody());
            String question = body.path("question").asText();
            String report = question.toLowerCase().contains("product") ? "sales_by_product" :
                    question.toLowerCase().contains("region") ? "sales_by_region" : "sales_summary";
            String dimension = report.equals("sales_by_product") ? "product" : "region";
            boolean summary = report.equals("sales_summary");
            String label = report.equals("sales_by_product") ? "Webcam" : report.equals("sales_by_region") ? "West" : "Total";
            double revenue = report.equals("sales_by_product") ? 2602860.05 :
                    report.equals("sales_by_region") ? 1956883.03 : 7789520.15;
            String dimensions = summary ? "[]" : "[\"" + dimension + "\"]";
            int limit = summary ? 1 : 20;
            String response = """
                    {"report":"%s","chartType":"bar","executedSql":"SELECT revenue FROM sample_sales",
                     "queryPlan":{"sourceTable":"sample_sales","dimensions":%s,
                       "metrics":[{"name":"revenue","sourceColumns":["amount","quantity"],"aggregation":"SUM_PRODUCT"}],
                       "filters":[],"rowLimit":%d},"rows":[{"label":"%s","revenue":%s}]}
                    """.formatted(report, dimensions, limit, label, revenue);
            byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();
        try {
            var controller = new GoldenEvaluationController(mapper,
                    "http://127.0.0.1:" + server.getAddress().getPort(), 1.0, Runnable::run);

            var report = controller.runAskYourData();

            assertEquals("askyourdata-v1", report.dataset());
            assertEquals(50, report.total());
            assertEquals(50, report.passed());
            assertEquals(1.0, report.passRate());
            assertTrue(report.qualityGatePassed());
            assertEquals(50, requests.get());
            assertTrue(report.cases().stream().allMatch(result -> result.planValid() && result.queryIsSelect()));
            assertTrue(report.notice().contains("not persisted"));
        } finally {
            server.stop(0);
        }
    }

    @Test void rejectsANonLoopbackAskYourDataOrigin() {
        assertThrows(IllegalArgumentException.class, () -> new GoldenEvaluationController(
                mapper, "http://example.com:8088", 1.0, Runnable::run));
        assertThrows(IllegalArgumentException.class, () -> new GoldenEvaluationController(
                mapper, "http://127.0.0.1:8088/path", 1.0, Runnable::run));
    }
}
