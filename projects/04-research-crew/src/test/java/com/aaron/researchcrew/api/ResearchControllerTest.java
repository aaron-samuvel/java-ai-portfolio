package com.aaron.researchcrew.api;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class ResearchControllerTest {
    @Test void requiresApprovalAndReturnsCitedLocalFindings() {
        ResearchController controller = new ResearchController();
        var plan = controller.plan(new ResearchController.PlanRequest("RAG retrieval grounding"));
        assertEquals("PENDING_APPROVAL", plan.status());
        assertThrows(org.springframework.web.server.ResponseStatusException.class,
                () -> controller.approve(plan.id(), new ResearchController.Approval(false)));
        var report = controller.approve(plan.id(), new ResearchController.Approval(true));
        assertTrue(report.criticPassed());
        assertFalse(report.findings().isEmpty());
        assertEquals(java.util.List.of("planner", "human-approval", "researcher", "writer", "critic"), report.stages());
        assertTrue(report.findings().stream().allMatch(f -> f.citation().matches("\\[S[1-4]\\]")));
    }

    @Test void graphDoesNotInventFindingsWithoutMatchingEvidence() {
        var controller = new ResearchController();
        var plan = controller.plan(new ResearchController.PlanRequest("astronomy volcanology"));
        var report = controller.approve(plan.id(), new ResearchController.Approval(true));
        assertTrue(report.criticPassed());
        assertTrue(report.findings().isEmpty());
        assertTrue(report.sources().isEmpty());
        assertTrue(report.notice().contains("will not invent"));
    }

    @Test void allowsBoundedEditsOnlyBeforeApproval() {
        ResearchController controller = new ResearchController();
        var plan = controller.plan(new ResearchController.PlanRequest("RAG"));
        var edited = controller.editPlan(plan.id(), new ResearchController.PlanRequest("prompt injection untrusted"));
        assertEquals("prompt injection untrusted", edited.topic());
        var report = controller.approve(plan.id(), new ResearchController.Approval(true));
        assertTrue(report.findings().stream().anyMatch(f -> f.statement().contains("untrusted input")));
        assertThrows(org.springframework.web.server.ResponseStatusException.class,
                () -> controller.editPlan(plan.id(), new ResearchController.PlanRequest("change after approval")));
    }
}
