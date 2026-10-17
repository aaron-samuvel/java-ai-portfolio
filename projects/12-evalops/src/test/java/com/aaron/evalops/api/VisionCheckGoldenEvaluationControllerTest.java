package com.aaron.evalops.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class VisionCheckGoldenEvaluationControllerTest {
    private final ObjectMapper mapper = new ObjectMapper();

    @Test void uploadsImageChecksReviewQueueAndPersistsHumanCorrection() throws Exception {
        var uploadBody = new AtomicReference<byte[]>();
        var uploadContentType = new AtomicReference<String>();
        var correctionBody = new AtomicReference<String>();
        var reviewListCalls = new AtomicInteger();
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/classify", exchange -> {
            uploadContentType.set(exchange.getRequestHeaders().getFirst("Content-Type"));
            uploadBody.set(exchange.getRequestBody().readAllBytes());
            reply(exchange, 200, """
                    {"reviewId":"review-123","label":"blue-dominant scene",
                     "candidates":[{"label":"blue-dominant scene","score":0.46},
                       {"label":"uncertain visual pattern","score":0.24},
                       {"label":"green-dominant scene","score":0.05}],
                     "width":24,"height":16,"status":"REVIEW_REQUIRED"}
                    """);
        });
        server.createContext("/api/classification-reviews", exchange -> {
            if ("PUT".equals(exchange.getRequestMethod())) {
                correctionBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
                reply(exchange, 200, """
                        {"id":"review-123","predictedLabel":"blue-dominant scene",
                         "correctedLabel":"human-confirmed blue scene","width":24,"height":16,"status":"REVIEWED"}
                        """);
            } else {
                if (reviewListCalls.getAndIncrement() == 0) {
                    reply(exchange, 200, """
                            [{"id":"review-123","predictedLabel":"blue-dominant scene",
                              "width":24,"height":16,"status":"PENDING"}]
                            """);
                } else reply(exchange, 200, "[]");
            }
        });
        server.start();
        try {
            var controller = new VisionCheckGoldenEvaluationController(mapper,
                    "http://127.0.0.1:" + server.getAddress().getPort(), 1.0, Runnable::run);

            var report = controller.run();

            assertEquals("visioncheck-v1", report.dataset());
            assertEquals(1, report.total());
            assertEquals(1, report.passed());
            assertEquals(1.0, report.passRate());
            assertTrue(report.qualityGatePassed());
            var result = report.cases().getFirst();
            assertEquals("visioncheck-blue-review-v1", result.id());
            assertTrue(result.labelMatched());
            assertTrue(result.candidatesValid());
            assertTrue(result.dimensionsMatched());
            assertTrue(result.reviewRequired());
            assertTrue(result.reviewCorrectionSaved());
            assertTrue(result.reviewQueueCleared());
            assertEquals(2, reviewListCalls.get());
            assertTrue(uploadContentType.get().startsWith("multipart/form-data; boundary="));
            String request = new String(uploadBody.get(), StandardCharsets.ISO_8859_1);
            assertTrue(request.contains("name=\"image\"; filename=\"visioncheck-blue.png\""));
            assertTrue(request.contains("Content-Type: image/png\r\n\r\n"));
            assertTrue(contains(uploadBody.get(), new byte[]{(byte) 0x89, 'P', 'N', 'G'}));
            assertEquals("human-confirmed blue scene", mapper.readTree(correctionBody.get()).path("correctedLabel").asText());
        } finally {
            server.stop(0);
        }
    }

    @Test void acceptsOnlyLoopbackVisionCheckOrigins() {
        new VisionCheckGoldenEvaluationController(mapper, "http://127.0.0.1:18087", 1.0, Runnable::run);
        new VisionCheckGoldenEvaluationController(mapper, "http://localhost:8087", 1.0, Runnable::run);
        assertThrows(IllegalArgumentException.class, () -> new VisionCheckGoldenEvaluationController(
                mapper, "http://example.com:8087", 1.0, Runnable::run));
        assertThrows(IllegalArgumentException.class, () -> new VisionCheckGoldenEvaluationController(
                mapper, "http://127.0.0.1:8087/path", 1.0, Runnable::run));
    }

    private static boolean contains(byte[] source, byte[] needle) {
        outer: for (int i = 0; i <= source.length - needle.length; i++) {
            for (int j = 0; j < needle.length; j++) if (source[i + j] != needle[j]) continue outer;
            return true;
        }
        return false;
    }

    private static void reply(com.sun.net.httpserver.HttpExchange exchange, int status, String json) throws java.io.IOException {
        byte[] body = json.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, body.length);
        exchange.getResponseBody().write(body);
        exchange.close();
    }
}
