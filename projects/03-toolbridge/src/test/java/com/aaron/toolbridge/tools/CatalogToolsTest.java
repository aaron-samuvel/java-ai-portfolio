package com.aaron.toolbridge.tools;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CatalogToolsTest {
    private final CatalogTools tools = new CatalogTools();
    @Test void searchesOnlyTheBoundedLocalCatalog() {
        assertFalse(tools.searchArtifacts("spring").isEmpty());
        assertThrows(IllegalArgumentException.class, () -> tools.searchArtifacts(" "));
    }
    @Test void formatsOnlySafeMavenCoordinates() {
        assertTrue(tools.getMavenCoordinate("org.example", "safe-lib").contains("safe-lib"));
        assertThrows(IllegalArgumentException.class, () -> tools.getMavenCoordinate("org.example", "bad/id"));
    }
}
