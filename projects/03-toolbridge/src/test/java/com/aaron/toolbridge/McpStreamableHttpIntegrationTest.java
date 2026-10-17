package com.aaron.toolbridge;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class McpStreamableHttpIntegrationTest {
    @LocalServerPort int port;
    @Autowired ObjectMapper objectMapper;

    @Test void initializesListsToolsAndCallsValidatedCoordinateToolOverMcpHttp() throws Exception {
        HttpClient client = HttpClient.newHttpClient();
        URI endpoint = URI.create("http://127.0.0.1:" + port + "/mcp");

        HttpResponse<String> initialized = post(client, endpoint, null, Map.of(
                "jsonrpc", "2.0", "id", 1, "method", "initialize", "params", Map.of(
                        "protocolVersion", "2025-03-26", "capabilities", Map.of(),
                        "clientInfo", Map.of("name", "toolbridge-integration-test", "version", "1.0"))));
        assertEquals(200, initialized.statusCode(), initialized.body());
        String session = initialized.headers().firstValue("Mcp-Session-Id").orElseThrow();
        JsonNode initializeResult = objectMapper.readTree(initialized.body()).path("result");
        assertEquals("2025-03-26", initializeResult.path("protocolVersion").asText());
        assertEquals("toolbridge", initializeResult.path("serverInfo").path("name").asText());

        HttpResponse<String> ready = post(client, endpoint, session, Map.of(
                "jsonrpc", "2.0", "method", "notifications/initialized"));
        assertEquals(202, ready.statusCode(), ready.body());

        HttpResponse<String> listed = post(client, endpoint, session, Map.of(
                "jsonrpc", "2.0", "id", 2, "method", "tools/list"));
        assertEquals(200, listed.statusCode(), listed.body());
        JsonNode tools = readMcpMessage(listed.body()).path("result").path("tools");
        assertEquals(5, tools.size());
        assertTrue(containsTool(tools, "get_maven_coordinate"));
        assertTrue(containsTool(tools, "query_sample_sales"));

        HttpResponse<String> called = post(client, endpoint, session, Map.of(
                "jsonrpc", "2.0", "id", 3, "method", "tools/call", "params", Map.of(
                        "name", "get_maven_coordinate",
                        "arguments", Map.of("groupId", "org.example", "artifactId", "safe-lib"))));
        assertEquals(200, called.statusCode(), called.body());
        JsonNode callResult = readMcpMessage(called.body()).path("result");
        assertFalse(callResult.path("isError").asBoolean());
        assertTrue(callResult.path("content").get(0).path("text").asText().contains(
                "<artifactId>safe-lib</artifactId>"));
    }

    private HttpResponse<String> post(HttpClient client, URI endpoint, String session, Object payload) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder(endpoint)
                .header("Content-Type", "application/json")
                .header("Accept", "application/json, text/event-stream")
                .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(payload)));
        if (session == null) {
            request.header("MCP-Protocol-Version", "2025-03-26");
        } else {
            request.header("Mcp-Session-Id", session).header("MCP-Protocol-Version", "2025-03-26");
        }
        return client.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }

    private boolean containsTool(JsonNode tools, String name) {
        for (JsonNode tool : tools) if (name.equals(tool.path("name").asText())) return true;
        return false;
    }

    private JsonNode readMcpMessage(String response) throws Exception {
        if (response.stripLeading().startsWith("{")) return objectMapper.readTree(response);
        for (String line : response.split("\\R")) {
            if (line.startsWith("data:")) return objectMapper.readTree(line.substring("data:".length()).strip());
        }
        throw new AssertionError("MCP response contained neither JSON nor an SSE data event: " + response);
    }
}
