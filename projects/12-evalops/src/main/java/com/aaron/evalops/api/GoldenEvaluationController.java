package com.aaron.evalops.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.io.ClassPathResource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;

/** Runs versioned, local golden cases through a fixed AskYourData adapter. */
@RestController
@RequestMapping("/api/evaluations/golden")
public class GoldenEvaluationController {
    private final ObjectMapper mapper;
    private final HttpClient client;
    private final URI askYourDataBase;
    private final double minimumPassRate;
    private final Executor caseExecutor;

    public GoldenEvaluationController(ObjectMapper mapper) {
        this(mapper, "http://127.0.0.1:8088", 0.80, Runnable::run);
    }

    @Autowired
    public GoldenEvaluationController(ObjectMapper mapper,
            @Value("${evalops.askyourdata-base-url:http://127.0.0.1:8088}") String baseUrl,
            @Value("${evalops.gates.askyourdata-minimum-pass-rate:0.80}") double minimumPassRate,
            @Qualifier("evalOpsCaseExecutor") Executor caseExecutor) {
        QualityGate.validateMinimum(minimumPassRate);
        this.mapper = mapper;
        this.askYourDataBase = validateLoopbackBase(baseUrl);
        this.minimumPassRate = minimumPassRate;
        this.caseExecutor = caseExecutor;
        this.client = HttpClient.newBuilder().connectTimeout(Duration.ofMillis(500)).build();
    }

    private URI validateLoopbackBase(String value) {
        try {
            URI uri = URI.create(value);
            if (!"http".equalsIgnoreCase(uri.getScheme()) || uri.getUserInfo() != null || uri.getQuery() != null ||
                    uri.getFragment() != null || !("127.0.0.1".equals(uri.getHost()) || "localhost".equalsIgnoreCase(uri.getHost())) ||
                    uri.getPort() < 1 || uri.getPort() > 65535 || !(uri.getPath().isEmpty() || uri.getPath().equals("/"))) {
                throw new IllegalArgumentException("EvalOps AskYourData URL must be an HTTP loopback origin.");
            }
            return uri;
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("EvalOps AskYourData URL must be an HTTP loopback origin.", e);
        }
    }

    @GetMapping("/askyourdata")
    public GoldenReport runAskYourData() {
        Instant startedAt = Instant.now();
        List<GoldenCase> cases;
        try {
            cases = loadCases();
        } catch (Exception e) {
            return goldenReport(Instant.now(), List.of(),
                    "Golden dataset could not be loaded; no application response is recorded.");
        }

        List<GoldenResult> results = BoundedEvaluationRunner.run(cases, caseExecutor, this::evaluate,
                (testCase, reason) -> new GoldenResult(testCase.id(), false, reason,
                        false, false, false, false, false));
        return goldenReport(startedAt, results,
                "Calls only the fixed loopback AskYourData adapter. Questions and full responses are not persisted.");
    }

    private GoldenReport goldenReport(Instant startedAt, List<GoldenResult> results, String notice) {
        long passed = results.stream().filter(GoldenResult::passed).count();
        QualityGate.Result gate = QualityGate.evaluate(passed, results.size(), minimumPassRate);
        return new GoldenReport("askyourdata-v1", startedAt, results, passed, results.size(), gate.passRate(),
                gate.minimumPassRate(), gate.qualityGatePassed(), notice);
    }

    private List<GoldenCase> loadCases() throws Exception {
        var resource = new ClassPathResource("golden/askyourdata.jsonl");
        try (var reader = new BufferedReader(new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8))) {
            List<GoldenCase> cases = new ArrayList<>();
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank()) continue;
                JsonNode json = mapper.readTree(line);
                List<String> dimensions = new ArrayList<>();
                JsonNode dimensionArray = json.path("expectedDimensions");
                if (dimensionArray.isArray()) dimensionArray.forEach(value -> dimensions.add(value.asText()));
                List<String> metricColumns = new ArrayList<>();
                JsonNode metricColumnArray = json.path("expectedMetricColumns");
                if (metricColumnArray.isArray()) metricColumnArray.forEach(value -> metricColumns.add(value.asText()));
                cases.add(new GoldenCase(json.path("id").asText(), json.path("question").asText(),
                        json.path("expectedReport").asText(), dimensions, json.path("expectedSource").asText(),
                        json.path("expectedMetric").asText(), metricColumns, json.path("expectedAggregation").asText(),
                        json.path("expectedPlanLimit").asInt(-1), json.path("expectedChartType").asText(),
                        json.path("expectedTopLabel").asText(), json.path("expectedTopRevenue").asDouble(Double.NaN),
                        json.path("numericTolerance").asDouble(0.01)));
            }
            return List.copyOf(cases);
        }
    }

    private GoldenResult evaluate(GoldenCase testCase) {
        try {
            String body = mapper.writeValueAsString(new Question(testCase.question()));
            HttpRequest request = HttpRequest.newBuilder(askYourDataBase.resolve("/api/analytics")).timeout(Duration.ofSeconds(3))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8)).build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() != 200) return new GoldenResult(testCase.id(), false, "HTTP_" + response.statusCode(), false, false, false, false, false);
            JsonNode actual = mapper.readTree(response.body());
            boolean reportMatches = actual.path("report").asText().equals(testCase.expectedReport());
            boolean chartMatches = actual.path("chartType").asText().equals(testCase.expectedChartType());
            boolean querySafe = actual.path("executedSql").asText().stripLeading().toUpperCase().startsWith("SELECT ");
            boolean planValid = matchesPlan(actual.path("queryPlan"), testCase);
            boolean hasRows = actual.path("rows").isArray() && !actual.path("rows").isEmpty();
            JsonNode top = hasRows ? actual.path("rows").get(0) : mapper.createObjectNode();
            boolean topLabelMatches = top.path("label").asText().equals(testCase.expectedTopLabel());
            double revenue = top.path("revenue").asDouble(Double.NaN);
            boolean numericMatches = Double.isFinite(revenue) && Double.isFinite(testCase.expectedTopRevenue())
                    && Math.abs(revenue - testCase.expectedTopRevenue()) <= testCase.numericTolerance();
            boolean passed = reportMatches && chartMatches && querySafe && planValid && hasRows && topLabelMatches && numericMatches;
            String detail = "report=" + reportMatches + ", chart=" + chartMatches + ", select=" + querySafe
                    + ", plan=" + planValid + ", rows=" + hasRows + ", topLabel=" + topLabelMatches + ", numeric=" + numericMatches;
            return new GoldenResult(testCase.id(), passed, detail, reportMatches, querySafe, planValid, topLabelMatches, numericMatches);
        } catch (Exception e) {
            return new GoldenResult(testCase.id(), false, "APP_UNAVAILABLE_OR_INVALID_RESPONSE", false, false, false, false, false);
        }
    }

    private boolean matchesPlan(JsonNode plan, GoldenCase testCase) {
        if (!plan.path("sourceTable").asText().equals(testCase.expectedSource()) ||
                !stringArrayMatches(plan.path("dimensions"), testCase.expectedDimensions()) ||
                !plan.path("filters").isArray() || !plan.path("filters").isEmpty() ||
                plan.path("rowLimit").asInt(-1) != testCase.expectedPlanLimit()) return false;
        JsonNode metrics = plan.path("metrics");
        if (!metrics.isArray() || metrics.size() != 1) return false;
        JsonNode metric = metrics.get(0);
        return metric.path("name").asText().equals(testCase.expectedMetric()) &&
                stringArrayMatches(metric.path("sourceColumns"), testCase.expectedMetricColumns()) &&
                metric.path("aggregation").asText().equals(testCase.expectedAggregation());
    }

    private boolean stringArrayMatches(JsonNode actual, List<String> expected) {
        if (!actual.isArray() || actual.size() != expected.size()) return false;
        for (int i = 0; i < expected.size(); i++) if (!actual.get(i).asText().equals(expected.get(i))) return false;
        return true;
    }

    private record GoldenCase(String id, String question, String expectedReport, List<String> expectedDimensions,
                              String expectedSource, String expectedMetric, List<String> expectedMetricColumns,
                              String expectedAggregation, int expectedPlanLimit, String expectedChartType,
                              String expectedTopLabel, double expectedTopRevenue, double numericTolerance) { }
    private record Question(String question) { }
    public record GoldenResult(String id, boolean passed, String detail, boolean reportMatched, boolean queryIsSelect,
                               boolean planValid, boolean topLabelMatched, boolean numericMatched) { }
    public record GoldenReport(String dataset, Instant startedAt, List<GoldenResult> cases, long passed, long total,
                               double passRate, double minimumPassRate, boolean qualityGatePassed, String notice) { }
}
