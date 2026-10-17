package com.aaron.evalops.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class PaperTrailGoldenEvaluationControllerTest {
    @Test void uploadsTheBundledPdfAndEvaluatesEveryExpectedInvoiceField() throws Exception {
        var receivedBody = new AtomicReference<byte[]>();
        var receivedContentType = new AtomicReference<String>();
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/invoices/extract-file", exchange -> {
            receivedContentType.set(exchange.getRequestHeaders().getFirst("Content-Type"));
            receivedBody.set(exchange.getRequestBody().readAllBytes());
            byte[] response = """
                    {"invoiceNumber":"INV-PT-2026-001","supplier":"Northstar Supplies","buyer":"Aaron S",
                     "currency":"INR","gstin":"27AABCU9603R1ZN",
                     "lineItems":[{"description":"Widget","quantity":2,"unitPrice":10.0,"amount":20.0}],
                     "lineItemsTotal":20.0,"tax":1.6,"total":21.6,"status":"EXTRACTED_REVIEW_BEFORE_PAYMENT"}
                    """.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.start();
        try {
            var controller = new PaperTrailGoldenEvaluationController(new ObjectMapper(),
                    "http://127.0.0.1:" + server.getAddress().getPort(), 1.0, Runnable::run);

            var report = controller.run();

            assertEquals("papertrail-v1", report.dataset());
            assertEquals(1, report.total());
            assertEquals(1, report.passed());
            assertEquals(1.0, report.passRate());
            assertTrue(report.qualityGatePassed());
            var result = report.cases().get(0);
            assertEquals("papertrail-invoice-v1", result.id());
            assertTrue(result.invoiceNumberMatched());
            assertTrue(result.partiesMatched());
            assertTrue(result.identityMatched());
            assertTrue(result.lineItemMatched());
            assertTrue(result.arithmeticReconciled());
            assertTrue(result.reviewStatusMatched());
            assertTrue(receivedContentType.get().startsWith("multipart/form-data; boundary="));
            String request = new String(receivedBody.get(), StandardCharsets.ISO_8859_1);
            assertTrue(request.contains("name=\"file\"; filename=\"invoice-extraction-fixture.pdf\""));
            assertTrue(request.contains("Content-Type: application/pdf\r\n\r\n%PDF-"));
        } finally {
            server.stop(0);
        }
    }

    @Test void rejectsNonLoopbackPaperTrailOrigins() {
        assertThrows(IllegalArgumentException.class, () -> new PaperTrailGoldenEvaluationController(
                new ObjectMapper(), "http://example.com:8091", 1.0, Runnable::run));
        assertThrows(IllegalArgumentException.class, () -> new PaperTrailGoldenEvaluationController(
                new ObjectMapper(), "http://127.0.0.1:8091/path", 1.0, Runnable::run));
    }
}
