package com.aaron.documind.api;

import com.aaron.documind.domain.CollectionRepository;
import com.aaron.documind.domain.DocumentCollection;
import com.aaron.documind.domain.DocumentStatus;
import com.aaron.documind.domain.StoredDocumentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

@SpringBootTest(properties = {"documind.storage.directory=target/test-uploads/documind-integration"})
@AutoConfigureMockMvc
@Import(DocumentEmbeddingPostgresIntegrationTest.DeterministicEmbeddingConfiguration.class)
@Testcontainers(disabledWithoutDocker = true)
class DocumentEmbeddingPostgresIntegrationTest {
    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(
            DockerImageName.parse("pgvector/pgvector:pg16").asCompatibleSubstituteFor("postgres"))
            .withDatabaseName("documind_api_test").withUsername("documind").withPassword("documind-test");

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired MockMvc mvc;
    @Autowired CollectionRepository collections;
    @Autowired StoredDocumentRepository documents;
    @Autowired VectorStore vectorStore;
    @MockitoBean ChatModel chatModel;
    private DocumentCollection collection;

    @BeforeEach
    void prepareCollectionAndDeterministicEmbeddingModel() {
        when(chatModel.call(any(Prompt.class))).thenReturn(new ChatResponse(List.of(
                new Generation(new AssistantMessage("The lunar samples contain basalt. [1]")))));
        collection = collections.save(new DocumentCollection("end-to-end ingestion"));
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class DeterministicEmbeddingConfiguration {
        @Bean
        @Primary
        EmbeddingModel integrationEmbeddingModel() {
            return new EmbeddingModel() {
                @Override public EmbeddingResponse call(EmbeddingRequest request) {
                    List<Embedding> embeddings = IntStream.range(0, request.getInstructions().size())
                            .mapToObj(index -> new Embedding(vector(), index)).toList();
                    return new EmbeddingResponse(embeddings);
                }
                @Override public float[] embed(Document document) { return vector(); }
                @Override public int dimensions() { return 768; }
                private float[] vector() {
                    float[] vector = new float[768];
                    vector[0] = 1.0f;
                    return vector;
                }
            };
        }
    }

    @Test
    void acceptsUploadIndexesWithEmbeddingsAndRetrievesFromItsCollection() throws Exception {
        String content = ("The moon is made of basalt and anorthosite. The Apollo missions returned samples "
                + "that scientists use to study lunar geology, impact history, and the age of the solar system. ").repeat(8);
        MockMultipartFile file = new MockMultipartFile("file", "lunar-notes.md", "text/markdown",
                content.getBytes(StandardCharsets.UTF_8));

        String response = mvc.perform(multipart("/api/collections/{id}/documents", collection.getId()).file(file))
                .andExpect(status().isAccepted()).andReturn().getResponse().getContentAsString();
        UUID documentId = UUID.fromString(new com.fasterxml.jackson.databind.ObjectMapper().readTree(response).path("id").asText());

        Instant deadline = Instant.now().plus(Duration.ofSeconds(10));
        var stored = documents.findById(documentId).orElseThrow();
        while (stored.getStatus() == DocumentStatus.PROCESSING && Instant.now().isBefore(deadline)) {
            Thread.sleep(20);
            stored = documents.findById(documentId).orElseThrow();
        }
        assertEquals(DocumentStatus.INDEXED, stored.getStatus(), stored.getErrorMessage());

        List<Document> matches = vectorStore.similaritySearch(SearchRequest.builder()
                .query("lunar basalt geology")
                .topK(5)
                .similarityThreshold(0.0)
                .filterExpression("collectionId == '" + collection.getId() + "'")
                .build());
        assertFalse(matches.isEmpty());
        assertTrue(matches.stream().allMatch(match -> documentId.toString().equals(match.getMetadata().get("documentId"))));
        assertTrue(matches.stream().anyMatch(match -> "lunar-notes.md".equals(match.getMetadata().get("fileName"))));

        mvc.perform(post("/api/collections/{id}/ask", collection.getId())
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content("{\"question\":\"What do the samples contain?\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.answer").value("The lunar samples contain basalt. [1]"))
                .andExpect(jsonPath("$.sources[0].fileName").value("lunar-notes.md"));

        mvc.perform(multipart("/api/collections/{id}/documents", collection.getId()).file(file))
                .andExpect(status().isConflict());
    }
}
