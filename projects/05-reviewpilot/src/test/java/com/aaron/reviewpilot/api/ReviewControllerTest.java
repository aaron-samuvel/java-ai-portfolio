package com.aaron.reviewpilot.api;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class ReviewControllerTest {
    @Test void scansAddedLinesAndReportsFileAndNewFileLineNumbers() {
        String diff = "diff --git a/src/Example.java b/src/Example.java\n--- a/src/Example.java\n+++ b/src/Example.java\n@@ -10,3 +10,4 @@\n context\n-TODO removed\n+TODO finish this\n+api_key=abcdefghijklmnop\n-old secret=password123456789";
        var result = new ReviewController().review(new ReviewController.DiffRequest(diff));
        assertEquals(2, result.findings().size());
        assertEquals("P1", result.findings().get(1).severity());
        assertEquals("src/Example.java", result.findings().get(0).file());
        assertEquals(11, result.findings().get(0).newLine());
        assertEquals(12, result.findings().get(1).newLine());
        assertTrue(result.notice().contains("No GitHub API"));
    }
}
