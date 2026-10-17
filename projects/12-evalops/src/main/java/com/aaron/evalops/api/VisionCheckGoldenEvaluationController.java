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

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;

/** Checks the local image classification and human-review workflow against a synthetic PNG. */
@RestController
@RequestMapping("/api/evaluations/golden/visioncheck")
public class VisionCheckGoldenEvaluationController {
    private static final String FIXTURE = "golden/fixtures/visioncheck-blue.png";
    private final ObjectMapper mapper;
    private final URI visionCheckBase;
    private final double minimumPassRate;
    private final Executor caseExecutor;
    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofMillis(500)).build();

    public VisionCheckGoldenEvaluationController(ObjectMapper mapper) {
        this(mapper, "http://127.0.0.1:8087", 1.0, Runnable::run);
    }

    @Autowired
    public VisionCheckGoldenEvaluationController(ObjectMapper mapper,
            @Value("${evalops.visioncheck-base-url:http://127.0.0.1:8087}") String baseUrl,
            @Value("${evalops.gates.visioncheck-minimum-pass-rate:1.0}") double minimumPassRate,
            @Qualifier("evalOpsCaseExecutor") Executor caseExecutor) {
        QualityGate.validateMinimum(minimumPassRate);
        this.mapper = mapper;
        this.visionCheckBase = validateLoopbackBase(baseUrl);
        this.minimumPassRate = minimumPassRate;
        this.caseExecutor = caseExecutor;
    }

    private URI validateLoopbackBase(String value) {
        try {
            URI uri = URI.create(value);
            if (!"http".equalsIgnoreCase(uri.getScheme()) || uri.getUserInfo() != null || uri.getQuery() != null ||
                    uri.getFragment() != null || !("127.0.0.1".equals(uri.getHost()) || "localhost".equalsIgnoreCase(uri.getHost())) ||
                    uri.getPort() < 1 || uri.getPort() > 65535 || !(uri.getPath().isEmpty() || uri.getPath().equals("/"))) {
                throw new IllegalArgumentException("EvalOps VisionCheck URL must be an HTTP loopback origin.");
            }
            return uri;
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("EvalOps VisionCheck URL must be an HTTP loopback origin.", e);
        }
    }

    @GetMapping
    public VisionCheckReport run() {
        Instant startedAt = Instant.now();
        List<VisionCheckCase> cases;
        try { cases = loadCases(); }
        catch (Exception e) {
            return report(startedAt, List.of(),
                    "VisionCheck golden dataset could not be loaded; no application response is recorded.");
        }
        List<VisionCheckResult> results = BoundedEvaluationRunner.run(cases, caseExecutor,
                this::evaluate, (testCase, reason) -> failed(testCase.id(), reason));
        return report(startedAt, results,
                "Uploads only bundled synthetic images to the fixed loopback VisionCheck API; image and full responses are not retained.");
    }

    private VisionCheckReport report(Instant startedAt, List<VisionCheckResult> results, String notice) {
        long passed = results.stream().filter(VisionCheckResult::passed).count();
        QualityGate.Result gate = QualityGate.evaluate(passed, results.size(), minimumPassRate);
        return new VisionCheckReport("visioncheck-v1", startedAt, results, passed, results.size(),
                gate.passRate(), gate.minimumPassRate(), gate.qualityGatePassed(), notice);
    }

    private List<VisionCheckCase> loadCases() throws Exception {
        var resource = new ClassPathResource("golden/visioncheck.jsonl");
        try (var reader = new BufferedReader(new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8))) {
            List<VisionCheckCase> cases = new ArrayList<>();
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank()) continue;
                JsonNode json = mapper.readTree(line);
                if (!FIXTURE.equals(json.path("fixture").asText())) {
                    throw new IllegalArgumentException("VisionCheck golden case references an unsupported fixture");
                }
                JsonNode expected = json.path("expected");
                cases.add(new VisionCheckCase(json.path("id").asText(), FIXTURE,
                        expected.path("label").asText(), expected.path("width").asInt(-1),
                        expected.path("height").asInt(-1), expected.path("candidateCount").asInt(-1),
                        expected.path("reviewStatus").asText(), expected.path("correctedLabel").asText()));
            }
            return List.copyOf(cases);
        }
    }

    private VisionCheckResult evaluate(VisionCheckCase testCase) {
        try {
            byte[] image;
            try (var input = new ClassPathResource(testCase.fixture()).getInputStream()) { image = input.readAllBytes(); }
            String boundary = "EvalOps-" + java.util.UUID.randomUUID();
            HttpResponse<String> classified = client.send(HttpRequest.newBuilder(visionCheckBase.resolve("/api/classify"))
                    .timeout(Duration.ofSeconds(5)).header("Content-Type", "multipart/form-data; boundary=" + boundary)
                    .POST(HttpRequest.BodyPublishers.ofByteArray(multipart(boundary, image))).build(),
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (classified.statusCode() != 200) return failed(testCase.id(), "CLASSIFY_HTTP_" + classified.statusCode());

            JsonNode actual = mapper.readTree(classified.body());
            String reviewId = actual.path("reviewId").asText("");
            boolean label = actual.path("label").asText().equals(testCase.label());
            boolean dimensions = actual.path("width").asInt(-1) == testCase.width() &&
                    actual.path("height").asInt(-1) == testCase.height();
            boolean reviewRequired = actual.path("status").asText().equals(testCase.reviewStatus());
            boolean candidatesValid = validCandidates(actual.path("candidates"), testCase);
            if (reviewId.isBlank()) return result(testCase.id(), false, label, candidatesValid, dimensions,
                    reviewRequired, false, false, false, "classification returned no review ID");

            JsonNode pending = getJson("/api/classification-reviews");
            boolean reviewQueued = findPending(pending, reviewId, testCase);
            HttpResponse<String> corrected = client.send(HttpRequest.newBuilder(visionCheckBase.resolve(
                            "/api/classification-reviews/" + URLEncoder.encode(reviewId, StandardCharsets.UTF_8)))
                    .timeout(Duration.ofSeconds(5)).header("Content-Type", "application/json")
                    .PUT(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(new Correction(testCase.correctedLabel())), StandardCharsets.UTF_8))
                    .build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (corrected.statusCode() != 200) return result(testCase.id(), false, label, candidatesValid,
                    dimensions, reviewRequired, reviewQueued, false, false, "correction HTTP " + corrected.statusCode());
            JsonNode correctedJson = mapper.readTree(corrected.body());
            boolean correctionSaved = correctedJson.path("id").asText().equals(reviewId) &&
                    correctedJson.path("correctedLabel").asText().equals(testCase.correctedLabel()) &&
                    correctedJson.path("status").asText().equals("REVIEWED");
            JsonNode remaining = getJson("/api/classification-reviews");
            boolean queueCleared = remaining.isArray() && !containsId(remaining, reviewId);
            boolean passed = label && candidatesValid && dimensions && reviewRequired && reviewQueued && correctionSaved && queueCleared;
            String detail = "label=" + label + ", candidates=" + candidatesValid + ", dimensions=" + dimensions +
                    ", reviewRequired=" + reviewRequired + ", reviewQueued=" + reviewQueued +
                    ", correctionSaved=" + correctionSaved + ", queueCleared=" + queueCleared;
            return result(testCase.id(), passed, label, candidatesValid, dimensions, reviewRequired,
                    reviewQueued, correctionSaved, queueCleared, detail);
        } catch (Exception e) {
            return failed(testCase.id(), "APP_UNAVAILABLE_OR_INVALID_RESPONSE");
        }
    }

    private JsonNode getJson(String path) throws Exception {
        HttpResponse<String> response = client.send(HttpRequest.newBuilder(visionCheckBase.resolve(path))
                .timeout(Duration.ofSeconds(5)).GET().build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (response.statusCode() != 200) throw new IllegalStateException("review list HTTP " + response.statusCode());
        return mapper.readTree(response.body());
    }

    private boolean findPending(JsonNode pending, String reviewId, VisionCheckCase testCase) {
        if (!pending.isArray()) return false;
        for (JsonNode item : pending) {
            if (item.path("id").asText().equals(reviewId)) {
                return item.path("predictedLabel").asText().equals(testCase.label()) &&
                        item.path("width").asInt(-1) == testCase.width() &&
                        item.path("height").asInt(-1) == testCase.height() &&
                        item.path("status").asText().equals("PENDING") && !item.has("image") && !item.has("imageBytes");
            }
        }
        return false;
    }

    private boolean containsId(JsonNode pending, String id) {
        for (JsonNode item : pending) if (item.path("id").asText().equals(id)) return true;
        return false;
    }

    private boolean validCandidates(JsonNode candidates, VisionCheckCase testCase) {
        if (!candidates.isArray() || candidates.size() != testCase.candidateCount() || candidates.size() == 0 ||
                !candidates.get(0).path("label").asText().equals(testCase.label())) return false;
        for (JsonNode candidate : candidates) {
            double score = candidate.path("score").asDouble(Double.NaN);
            if (candidate.path("label").asText().isBlank() || !Double.isFinite(score) || score < 0 || score > 1) return false;
        }
        return true;
    }

    private byte[] multipart(String boundary, byte[] image) throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream(image.length + 256);
        output.write(("--" + boundary + "\r\nContent-Disposition: form-data; name=\"image\"; " +
                "filename=\"visioncheck-blue.png\"\r\nContent-Type: image/png\r\n\r\n").getBytes(StandardCharsets.UTF_8));
        output.write(image);
        output.write(("\r\n--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));
        return output.toByteArray();
    }

    private VisionCheckResult result(String id, boolean passed, boolean label, boolean candidates, boolean dimensions,
                                     boolean reviewRequired, boolean reviewQueued, boolean correctionSaved,
                                     boolean queueCleared, String detail) {
        return new VisionCheckResult(id, passed, label, candidates, dimensions, reviewRequired,
                reviewQueued, correctionSaved, queueCleared, detail);
    }

    private VisionCheckResult failed(String id, String reason) {
        return result(id, false, false, false, false, false, false, false, false, reason);
    }

    private record VisionCheckCase(String id, String fixture, String label, int width, int height,
                                   int candidateCount, String reviewStatus, String correctedLabel) { }
    private record Correction(String correctedLabel) { }
    public record VisionCheckResult(String id, boolean passed, boolean labelMatched, boolean candidatesValid,
                                    boolean dimensionsMatched, boolean reviewRequired, boolean reviewQueued,
                                    boolean reviewCorrectionSaved, boolean reviewQueueCleared, String detail) { }
    public record VisionCheckReport(String dataset, Instant startedAt, List<VisionCheckResult> cases,
                                    long passed, long total, double passRate, double minimumPassRate,
                                    boolean qualityGatePassed, String notice) { }
}
