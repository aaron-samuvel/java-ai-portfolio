package com.aaron.toolbridge.tools;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MavenCentralToolsTest {
    @Test void parsesOnlyBoundedCoordinateSummariesAndRejectsInvalidQueries() throws Exception {
        var tools = new MavenCentralTools(new ObjectMapper());
        var result = tools.parse("""
                {"response":{"docs":[{"g":"org.example","a":"demo","latestVersion":"1.2.3","p":"jar"}]}}
                """);
        assertEquals(1, result.size());
        assertEquals("org.example", result.getFirst().groupId());
        assertEquals("1.2.3", result.getFirst().latestVersion());
        assertThrows(IllegalArgumentException.class, () -> tools.searchMavenCentral(" "));
        assertThrows(IllegalArgumentException.class, () -> tools.searchMavenCentral("x".repeat(81)));
    }
}
