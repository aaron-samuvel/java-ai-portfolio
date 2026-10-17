package com.aaron.toolbridge.tools;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;

@Component
public class CatalogTools {
    private static final Logger log = LoggerFactory.getLogger(CatalogTools.class);
    private static final List<Artifact> ARTIFACTS = List.of(
            new Artifact("org.springframework.boot", "spring-boot-starter-web", "Spring MVC and embedded servlet container"),
            new Artifact("org.springframework.ai", "spring-ai-starter-model-ollama", "Spring AI integration for Ollama chat and embeddings"),
            new Artifact("org.springframework.ai", "spring-ai-starter-mcp-server-webmvc", "Spring AI MCP server over WebMVC"),
            new Artifact("org.postgresql", "postgresql", "PostgreSQL JDBC driver"),
            new Artifact("org.flywaydb", "flyway-core", "Database migration engine"));

    @Tool(name = "search_sample_artifacts", description = "Search a small local sample catalog of Maven artifacts by group, artifact, or description")
    public List<Artifact> searchArtifacts(
            @ToolParam(description = "Search text, 1 to 80 characters") String query) {
        if (query == null || query.isBlank() || query.length() > 80) throw new IllegalArgumentException("query must contain 1–80 characters");
        String q = query.toLowerCase(Locale.ROOT);
        List<Artifact> result = ARTIFACTS.stream().filter(a -> (a.groupId() + " " + a.artifactId() + " " + a.description()).toLowerCase(Locale.ROOT).contains(q)).limit(10).toList();
        log.info("mcp_tool name=search_sample_artifacts queryLength={} resultCount={}", query.length(), result.size());
        return result;
    }

    @Tool(name = "get_maven_coordinate", description = "Format a validated Maven coordinate from a group ID and artifact ID")
    public String getMavenCoordinate(
            @ToolParam(description = "Maven group ID, for example org.springframework.ai") String groupId,
            @ToolParam(description = "Maven artifact ID") String artifactId) {
        if (!validCoordinatePart(groupId) || !validCoordinatePart(artifactId)) throw new IllegalArgumentException("Coordinate parts may contain only letters, digits, dot, underscore, and hyphen");
        log.info("mcp_tool name=get_maven_coordinate group={} artifact={}", groupId, artifactId);
        return "<dependency><groupId>%s</groupId><artifactId>%s</artifactId></dependency>".formatted(groupId, artifactId);
    }

    private boolean validCoordinatePart(String value) {
        return value != null && !value.isBlank() && value.length() <= 120 && value.matches("[A-Za-z0-9_.-]+");
    }

    public record Artifact(String groupId, String artifactId, String description) {}
}
