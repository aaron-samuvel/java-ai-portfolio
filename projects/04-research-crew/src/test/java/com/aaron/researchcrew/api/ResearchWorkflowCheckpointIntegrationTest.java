package com.aaron.researchcrew.api;

import org.bsc.langgraph4j.checkpoint.PostgresSaverV2;
import org.bsc.langgraph4j.RunnableConfig;
import org.bsc.langgraph4j.serializer.std.ObjectStreamStateSerializer;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@Testcontainers(disabledWithoutDocker = true)
class ResearchWorkflowCheckpointIntegrationTest {
    @Container
    private static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(
            DockerImageName.parse("pgvector/pgvector:pg16"))
            .withDatabaseName("research_crew_test")
            .withUsername("research")
            .withPassword("research-test-password");

    @Test
    void restoresCompletedReportAfterBuildingANewGraphAndSaver() throws Exception {
        String runId = UUID.randomUUID().toString();
        var firstSaver = saver();
        var completed = new ResearchWorkflowGraph(firstSaver).execute(runId, "RAG retrieval grounding", 2);
        assertEquals(2, completed.revisions());
        assertFalse(completed.findings().isEmpty());
        assertFalse(firstSaver.list(RunnableConfig.builder().threadId(runId).build()).isEmpty());

        var restartedController = new ResearchController(new ResearchWorkflowGraph(saver()));
        var restoredReport = restartedController.approve(runId, new ResearchController.Approval(true));
        assertEquals("Research draft: RAG retrieval grounding", restoredReport.title());
        assertEquals(2, restoredReport.revisions());
        assertEquals(completed.findings(), restoredReport.findings());
        assertEquals(completed.sources().stream().map(source -> new ResearchController.Source(
                source.id(), source.excerpt(), source.uri())).toList(), restoredReport.sources());
        assertTrue(restoredReport.criticPassed());
    }

    private PostgresSaverV2 saver() throws Exception {
        return PostgresSaverV2.builder()
                .host(postgres.getHost())
                .port(postgres.getFirstMappedPort())
                .database(postgres.getDatabaseName())
                .user(postgres.getUsername())
                .password(postgres.getPassword())
                .stateSerializer(new ObjectStreamStateSerializer<>(ResearchWorkflowGraph.WorkflowState::new))
                .createTables(true)
                .build();
    }
}
