package com.aaron.evalops.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.io.ClassPathResource;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Measures retrieval evidence recall and citation integrity against a fixed local DocuMind app. */
@RestController
@RequestMapping("/api/evaluations/golden/documind")
public class DocuMindGoldenEvaluationController {
    private static final Pattern CITATION = Pattern.compile("\\[(\\d{1,3})]");
    private final ObjectMapper mapper;
    private final URI documindBase;
    private final double minimumPassRate;
    private final Executor caseExecutor;
    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofMillis(500)).build();

    public DocuMindGoldenEvaluationController(ObjectMapper mapper,
            @Value("${evalops.documind-base-url:http://127.0.0.1:8081}") String baseUrl) {
        this(mapper, baseUrl, 0.67, Runnable::run);
    }

    @Autowired
    public DocuMindGoldenEvaluationController(ObjectMapper mapper,
            @Value("${evalops.documind-base-url:http://127.0.0.1:8081}") String baseUrl,
            @Value("${evalops.gates.documind-minimum-pass-rate:0.67}") double minimumPassRate,
            @Qualifier("evalOpsCaseExecutor") Executor caseExecutor) {
        QualityGate.validateMinimum(minimumPassRate);
        this.mapper = mapper;
        this.documindBase = validateLoopbackBase(baseUrl);
        this.minimumPassRate = minimumPassRate;
        this.caseExecutor = caseExecutor;
    }

    private URI validateLoopbackBase(String value) {
        try {
            URI uri = URI.create(value);
            if (!"http".equalsIgnoreCase(uri.getScheme()) || uri.getUserInfo() != null || uri.getQuery() != null ||
                    uri.getFragment() != null || !("127.0.0.1".equals(uri.getHost()) || "localhost".equalsIgnoreCase(uri.getHost())) ||
                    uri.getPort() < 1 || uri.getPort() > 65535 || !(uri.getPath().isEmpty() || uri.getPath().equals("/"))) {
                throw new IllegalArgumentException("EvalOps DocuMind URL must be an HTTP loopback origin.");
            }
            return uri;
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("EvalOps DocuMind URL must be an HTTP loopback origin.", e);
        }
    }

    @GetMapping
    public DocuMindReport run(@RequestParam UUID collectionId) {
        Instant startedAt = Instant.now();
        List<Case> cases;
        try { cases = loadCases(); }
        catch (Exception e) {
            return docuMindReport(startedAt, List.of(),
                    "DocuMind evidence cases could not be loaded; no application response is recorded.");
        }
        List<DocuMindResult> results = BoundedEvaluationRunner.run(cases, caseExecutor,
                testCase -> evaluate(collectionId, testCase),
                (testCase, reason) -> new DocuMindResult(testCase.id(), false, 0, false, false, reason));
        return docuMindReport(startedAt, results,
                "Calls only the fixed loopback DocuMind API. Full questions, answers and source text are not retained in the report.");
    }

    private DocuMindReport docuMindReport(Instant startedAt, List<DocuMindResult> results, String notice) {
        long passed = results.stream().filter(DocuMindResult::passed).count();
        QualityGate.Result gate = QualityGate.evaluate(passed, results.size(), minimumPassRate);
        return new DocuMindReport("documind-retrieval-v1", startedAt, results, passed, results.size(),
                gate.passRate(), gate.minimumPassRate(), gate.qualityGatePassed(), notice);
    }

    private List<Case> loadCases() throws Exception {
        var resource = new ClassPathResource("golden/documind.jsonl");
        try (var reader = new BufferedReader(new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8))) {
            List<Case> cases = new ArrayList<>();
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank()) continue;
                JsonNode json = mapper.readTree(line);
                cases.add(new Case(json.path("id").asText(), json.path("question").asText(),
                        json.path("expectedEvidence").asText(), json.path("minimumRecall").asDouble(0.66)));
            }
            return List.copyOf(cases);
        }
    }

    private DocuMindResult evaluate(UUID collectionId, Case testCase) {
        try {
            String body = mapper.writeValueAsString(new Question(testCase.question()));
            URI endpoint = documindBase.resolve("/api/collections/" + collectionId + "/ask");
            HttpRequest request = HttpRequest.newBuilder(endpoint).timeout(Duration.ofSeconds(30))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8)).build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() != 200) return new DocuMindResult(testCase.id(), false, 0, false, false, "HTTP_" + response.statusCode());

            JsonNode answer = mapper.readTree(response.body());
            JsonNode sources = answer.path("sources");
            List<String> snippets = new ArrayList<>();
            Set<Integer> sourceIds = new HashSet<>();
            if (sources.isArray()) for (JsonNode source : sources) {
                snippets.add(source.path("snippet").asText(""));
                if (source.path("n").canConvertToInt()) sourceIds.add(source.path("n").asInt());
            }
            double recall = DocuMindEvidenceMetrics.bestEvidenceRecall(testCase.expectedEvidence(), snippets);
            Matcher citations = CITATION.matcher(answer.path("answer").asText(""));
            boolean citationPresent = false;
            boolean citationsResolve = true;
            while (citations.find()) {
                citationPresent = true;
                if (!sourceIds.contains(Integer.parseInt(citations.group(1)))) citationsResolve = false;
            }
            boolean passed = recall >= testCase.minimumRecall() && citationPresent && citationsResolve;
            String detail = "evidenceRecall=" + String.format(java.util.Locale.ROOT, "%.2f", recall)
                    + ", citationPresent=" + citationPresent + ", citationsResolve=" + citationsResolve;
            return new DocuMindResult(testCase.id(), passed, recall, citationPresent, citationsResolve, detail);
        } catch (Exception e) {
            return new DocuMindResult(testCase.id(), false, 0, false, false, "APP_UNAVAILABLE_OR_INVALID_RESPONSE");
        }
    }

    private record Case(String id, String question, String expectedEvidence, double minimumRecall) { }
    private record Question(String question) { }
    public record DocuMindResult(String id, boolean passed, double evidenceRecall, boolean citationPresent,
                                 boolean citationsResolve, String detail) { }
    public record DocuMindReport(String dataset, Instant startedAt, List<DocuMindResult> cases,
                                 long passed, long total, double passRate, double minimumPassRate,
                                 boolean qualityGatePassed, String notice) { }
}
