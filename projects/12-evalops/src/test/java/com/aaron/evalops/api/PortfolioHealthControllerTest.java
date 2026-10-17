package com.aaron.evalops.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PortfolioHealthControllerTest {
    @Test void returnsSanitizedStatusForEveryPortfolioApp() {
        var result = new PortfolioHealthController(new ObjectMapper()).health();
        assertEquals(12, result.total());
        assertEquals(12, result.services().size());
        assertTrue(result.services().stream().allMatch(service -> service.status().equals("UP") || service.status().equals("DOWN")));
        assertTrue(result.services().stream().noneMatch(service -> service.name().contains("/actuator")));
    }
}
