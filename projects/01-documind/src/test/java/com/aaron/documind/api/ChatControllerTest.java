package com.aaron.documind.api;

import com.aaron.documind.domain.CollectionRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ChatControllerTest {
    @Test void streamReturnsCautiousFallbackAndEmptySourcesWhenRetrievalMisses() {
        CollectionRepository collections = mock(CollectionRepository.class);
        when(collections.existsById(any())).thenReturn(true);
        VectorStore vectorStore = mock(VectorStore.class);
        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of());
        ChatClient.Builder builder = mock(ChatClient.Builder.class);
        when(builder.build()).thenReturn(mock(ChatClient.class));

        var controller = new ChatController(collections, vectorStore, builder, new ObjectMapper(), 5, 0.55);
        var events = controller.stream(UUID.randomUUID(), "What is absent?").collectList().block();
        assertNotNull(events);
        assertEquals(List.of("token", "sources", "done"), events.stream().map(event -> event.event()).toList());
        assertEquals("I could not find that in your documents.", events.getFirst().data());
        assertEquals("[]", events.get(1).data());
        verifyNoInteractions(builder.build());
    }
}
