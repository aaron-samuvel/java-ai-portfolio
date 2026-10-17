package com.aaron.evalops.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.ByteArrayOutputStream;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.ArrayList;
import java.util.concurrent.Executor;

/** Verifies PaperTrail's structured extraction against one fixed synthetic invoice fixture. */
@RestController
@RequestMapping("/api/evaluations/golden/papertrail")
public class PaperTrailGoldenEvaluationController {
    private static final String FIXTURE = "fixtures/invoice-extraction-fixture.pdf";
    private final ObjectMapper mapper;
    private final URI paperTrailBase;
    private final double minimumPassRate;
    private final Executor caseExecutor;
    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofMillis(500)).build();

    public PaperTrailGoldenEvaluationController(ObjectMapper mapper) {
        this(mapper, "http://127.0.0.1:8091", 1.0, Runnable::run);
    }

    @Autowired
    public PaperTrailGoldenEvaluationController(ObjectMapper mapper,
            @Value("${evalops.papertrail-base-url:http://127.0.0.1:8091}") String baseUrl,
            @Value("${evalops.gates.papertrail-minimum-pass-rate:1.0}") double minimumPassRate,
            @Qualifier("evalOpsCaseExecutor") Executor caseExecutor) {
        QualityGate.validateMinimum(minimumPassRate);
        this.mapper = mapper;
        this.paperTrailBase = validateLoopbackBase(baseUrl);
        this.minimumPassRate = minimumPassRate;
        this.caseExecutor = caseExecutor;
    }

    private URI validateLoopbackBase(String value) {
        try {
            URI uri = URI.create(value);
            if (!"http".equalsIgnoreCase(uri.getScheme()) || uri.getUserInfo() != null || uri.getQuery() != null ||
                    uri.getFragment() != null || !("127.0.0.1".equals(uri.getHost()) || "localhost".equalsIgnoreCase(uri.getHost())) ||
                    uri.getPort() < 1 || uri.getPort() > 65535 || !(uri.getPath().isEmpty() || uri.getPath().equals("/"))) {
                throw new IllegalArgumentException("EvalOps PaperTrail URL must be an HTTP loopback origin.");
            }
            return uri;
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("EvalOps PaperTrail URL must be an HTTP loopback origin.", e);
        }
    }

    @GetMapping
    public PaperTrailReport run() {
        Instant startedAt = Instant.now();
        List<PaperTrailCase> goldenCases;
        try { goldenCases = loadCases(); }
        catch (Exception e) {
            return paperTrailReport(startedAt, List.of(),
                    "PaperTrail golden dataset could not be loaded; no application response is recorded.");
        }
        List<PaperTrailResult> results = BoundedEvaluationRunner.run(goldenCases, caseExecutor,
                this::evaluate, (testCase, reason) -> failed(testCase.id(), reason));
        return paperTrailReport(startedAt, results,
                "Uploads only the bundled synthetic invoice to the fixed loopback PaperTrail API; response content is not retained.");
    }

    private PaperTrailReport paperTrailReport(Instant startedAt, List<PaperTrailResult> results, String notice) {
        long passed = results.stream().filter(PaperTrailResult::passed).count();
        QualityGate.Result gate = QualityGate.evaluate(passed, results.size(), minimumPassRate);
        return new PaperTrailReport("papertrail-v1", startedAt, results, passed, results.size(),
                gate.passRate(), gate.minimumPassRate(), gate.qualityGatePassed(),
                notice);
    }

    private List<PaperTrailCase> loadCases() throws Exception {
        var resource = new ClassPathResource("golden/papertrail.jsonl");
        try (var reader = new BufferedReader(new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8))) {
            List<PaperTrailCase> cases = new ArrayList<>();
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank()) continue;
                JsonNode json = mapper.readTree(line);
                if (!FIXTURE.equals(json.path("fixture").asText())) {
                    throw new IllegalArgumentException("PaperTrail golden case references an unsupported fixture");
                }
                JsonNode expected = json.path("expected");
                cases.add(new PaperTrailCase(json.path("id").asText(), FIXTURE,
                        expected.path("invoiceNumber").asText(), expected.path("supplier").asText(),
                        expected.path("buyer").asText(), expected.path("currency").asText(),
                        expected.path("gstin").asText(), expected.path("itemDescription").asText(),
                        expected.path("quantity").asDouble(Double.NaN), expected.path("unitPrice").asDouble(Double.NaN),
                        expected.path("lineAmount").asDouble(Double.NaN), expected.path("subtotal").asDouble(Double.NaN),
                        expected.path("tax").asDouble(Double.NaN), expected.path("total").asDouble(Double.NaN),
                        expected.path("status").asText()));
            }
            return List.copyOf(cases);
        }
    }

    private PaperTrailResult evaluate(PaperTrailCase testCase) {
        try {
            byte[] pdf;
            try (var input = new ClassPathResource(testCase.fixture()).getInputStream()) {
                pdf = input.readAllBytes();
            }
            String boundary = "EvalOps-" + java.util.UUID.randomUUID();
            byte[] requestBody = multipart(boundary, pdf);
            HttpRequest request = HttpRequest.newBuilder(paperTrailBase.resolve("/api/invoices/extract-file"))
                    .timeout(Duration.ofSeconds(5))
                    .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                    .POST(HttpRequest.BodyPublishers.ofByteArray(requestBody)).build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() != 200) return failed(testCase.id(), "HTTP_" + response.statusCode());

            JsonNode actual = mapper.readTree(response.body());
            boolean invoice = actual.path("invoiceNumber").asText().equals(testCase.invoiceNumber());
            boolean parties = actual.path("supplier").asText().equals(testCase.supplier())
                    && actual.path("buyer").asText().equals(testCase.buyer());
            boolean identity = actual.path("currency").asText().equals(testCase.currency())
                    && actual.path("gstin").asText().equals(testCase.gstin());
            JsonNode item = actual.path("lineItems").isArray() && !actual.path("lineItems").isEmpty()
                    ? actual.path("lineItems").get(0) : mapper.createObjectNode();
            boolean lineItem = actual.path("lineItems").size() == 1 && item.path("description").asText().equals(testCase.itemDescription())
                    && close(item.path("quantity").asDouble(Double.NaN), testCase.quantity())
                    && close(item.path("unitPrice").asDouble(Double.NaN), testCase.unitPrice())
                    && close(item.path("amount").asDouble(Double.NaN), testCase.lineAmount());
            double subtotal = actual.path("lineItemsTotal").asDouble(Double.NaN);
            double tax = actual.path("tax").asDouble(Double.NaN);
            double total = actual.path("total").asDouble(Double.NaN);
            boolean arithmetic = close(subtotal, testCase.subtotal()) && close(tax, testCase.tax())
                    && close(total, testCase.total())
                    && close(subtotal + tax, total);
            boolean status = actual.path("status").asText().equals(testCase.status());
            boolean passed = invoice && parties && identity && lineItem && arithmetic && status;
            String detail = "invoice=" + invoice + ", parties=" + parties + ", identity=" + identity
                    + ", lineItem=" + lineItem + ", arithmetic=" + arithmetic + ", reviewStatus=" + status;
            return new PaperTrailResult(testCase.id(), passed, invoice, parties, identity,
                    lineItem, arithmetic, status, detail);
        } catch (Exception e) {
            return failed(testCase.id(), "APP_UNAVAILABLE_OR_INVALID_RESPONSE");
        }
    }

    private byte[] multipart(String boundary, byte[] pdf) throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream(pdf.length + 256);
        output.write(("--" + boundary + "\r\nContent-Disposition: form-data; name=\"file\"; " +
                "filename=\"invoice-extraction-fixture.pdf\"\r\nContent-Type: application/pdf\r\n\r\n")
                .getBytes(StandardCharsets.UTF_8));
        output.write(pdf);
        output.write(("\r\n--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));
        return output.toByteArray();
    }

    private boolean close(double actual, double expected) {
        return Double.isFinite(actual) && Math.abs(actual - expected) < 0.005;
    }

    private PaperTrailResult failed(String id, String reason) {
        return new PaperTrailResult(id, false, false, false,
                false, false, false, false, reason);
    }

    private record PaperTrailCase(String id, String fixture, String invoiceNumber, String supplier, String buyer,
                                  String currency, String gstin, String itemDescription, double quantity,
                                  double unitPrice, double lineAmount, double subtotal, double tax,
                                  double total, String status) { }
    public record PaperTrailResult(String id, boolean passed, boolean invoiceNumberMatched, boolean partiesMatched,
                                   boolean identityMatched, boolean lineItemMatched, boolean arithmeticReconciled,
                                   boolean reviewStatusMatched, String detail) { }
    public record PaperTrailReport(String dataset, Instant startedAt, List<PaperTrailResult> cases,
                                   long passed, long total, double passRate, double minimumPassRate,
                                   boolean qualityGatePassed, String notice) { }
}
