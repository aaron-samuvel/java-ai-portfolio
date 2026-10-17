package com.aaron.toolbridge.tools;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Read-only, cached lookups against Sonatype's documented Maven Central search API. */
@Component
public class MavenCentralTools {
    private static final Logger log = LoggerFactory.getLogger(MavenCentralTools.class);
    private static final String API = "https://search.maven.org/solrsearch/select";
    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).followRedirects(HttpClient.Redirect.NORMAL).build();
    private final ObjectMapper mapper;
    private final Map<String, List<Artifact>> cache = new ConcurrentHashMap<>();

    public MavenCentralTools(ObjectMapper mapper) { this.mapper = mapper; }

    @Tool(name="search_maven_central", description="Search Maven Central for public artifacts by group, artifact, or keyword; returns up to five cached coordinate/version summaries")
    public List<Artifact> searchMavenCentral(
            @ToolParam(description="Search phrase, 1 to 80 characters") String query) {
        if (query == null || query.isBlank() || query.length() > 80) throw new IllegalArgumentException("query must contain 1–80 characters");
        String normalized = query.strip().toLowerCase(Locale.ROOT);
        List<Artifact> previous = cache.get(normalized);
        if (previous != null) return previous;
        try {
            String encoded = URLEncoder.encode(query.strip(), StandardCharsets.UTF_8);
            URI uri = URI.create(API + "?q=" + encoded + "&rows=5&wt=json");
            HttpRequest request = HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(4))
                    .header("Accept", "application/json").header("User-Agent", "Java-AI-Portfolio-ToolBridge/0.1")
                    .GET().build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() != 200) return List.of();
            List<Artifact> artifacts = parse(response.body());
            if (cache.size() >= 100) cache.clear();
            cache.put(normalized, artifacts);
            log.info("mcp_tool name=search_maven_central queryLength={} resultCount={} cacheHit=false", query.length(), artifacts.size());
            return artifacts;
        } catch (Exception e) {
            log.warn("mcp_tool name=search_maven_central status=unavailable queryLength={}", query.length());
            return List.of();
        }
    }

    List<Artifact> parse(String json) throws Exception {
        JsonNode docs = mapper.readTree(json).path("response").path("docs");
        List<Artifact> results = new ArrayList<>();
        for (JsonNode doc : docs) {
            results.add(new Artifact(doc.path("g").asText(""), doc.path("a").asText(""),
                    doc.path("latestVersion").asText(""), doc.path("p").asText("")));
        }
        return List.copyOf(results);
    }

    public record Artifact(String groupId, String artifactId, String latestVersion, String packaging) { }
}
