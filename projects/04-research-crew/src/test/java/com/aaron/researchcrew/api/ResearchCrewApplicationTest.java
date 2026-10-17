package com.aaron.researchcrew.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@AutoConfigureMockMvc
class ResearchCrewApplicationTest {
    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper mapper;
    @Autowired private ResearchPlanStore planStore;

    @Test
    void springApiKeepsApprovalGateBeforeGraphExecution() throws Exception {
        String planBody = mvc.perform(post("/api/research/plans")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"topic\":\"RAG retrieval grounding\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING_APPROVAL"))
                .andReturn().getResponse().getContentAsString();
        JsonNode plan = mapper.readTree(planBody);
        String id = plan.path("id").asText();

        MvcResult stream = mvc.perform(get("/api/research/{id}/events", id))
                .andExpect(request().asyncStarted()).andReturn();

        mvc.perform(post("/api/research/{id}/approve", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"approved\":false}"))
                .andExpect(status().isConflict());

        mvc.perform(post("/api/research/{id}/approve", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"approved\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.criticPassed").value(true))
                .andExpect(jsonPath("$.findings.length()").value(org.hamcrest.Matchers.greaterThan(0)))
                .andExpect(jsonPath("$.stages[1]").value("human-approval"))
                .andExpect(jsonPath("$.stages[2]").value("researcher"));

        mvc.perform(get("/api/research/{id}/report.md", id))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content()
                        .contentTypeCompatibleWith("text/markdown"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content()
                        .string(org.hamcrest.Matchers.containsString("RAG retrieves relevant source passages")))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content()
                        .string(org.hamcrest.Matchers.containsString("Critic citation check: passed")));

        mvc.perform(asyncDispatch(stream))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content()
                        .contentTypeCompatibleWith(MediaType.TEXT_EVENT_STREAM))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content()
                        .string(org.hamcrest.Matchers.containsString("researcher")))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content()
                        .string(org.hamcrest.Matchers.containsString("Citation integrity check passed")))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content()
                        .string(org.hamcrest.Matchers.containsString("workflow")));
    }

    @Test
    void pendingPlanAndEditsSurviveControllerRestart() {
        var original = new ResearchController(new ResearchWorkflowGraph(), planStore);
        var created = original.plan(new ResearchController.PlanRequest("RAG"));
        var edited = original.editPlan(created.id(), new ResearchController.PlanRequest("prompt injection untrusted"));
        assertEquals("prompt injection untrusted", edited.topic());

        var restarted = new ResearchController(new ResearchWorkflowGraph(), planStore);
        var report = restarted.approve(created.id(), new ResearchController.Approval(true));

        assertEquals(1, report.revisions());
        assertTrue(report.title().contains("prompt injection untrusted"));
        assertTrue(report.findings().stream().anyMatch(f -> f.statement().contains("Retrieved documents are untrusted input")));
        assertTrue(planStore.findPending().stream().noneMatch(d -> d.id().equals(created.id())));
    }
}
