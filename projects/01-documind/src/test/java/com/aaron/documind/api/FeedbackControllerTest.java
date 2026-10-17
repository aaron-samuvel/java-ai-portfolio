package com.aaron.documind.api;

import com.aaron.documind.domain.AnswerFeedback;
import com.aaron.documind.domain.AnswerFeedbackRepository;
import com.aaron.documind.domain.CollectionRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class FeedbackControllerTest {
    @Test void storesTheQuestionAnswerSourcesAndRatingForAnExistingCollection() throws Exception {
        UUID collectionId = UUID.randomUUID();
        CollectionRepository collections = mock(CollectionRepository.class);
        when(collections.existsById(collectionId)).thenReturn(true);
        AnswerFeedbackRepository repository = mock(AnswerFeedbackRepository.class);
        when(repository.save(any(AnswerFeedback.class))).thenAnswer(invocation -> invocation.getArgument(0));
        var controller = new FeedbackController(collections, repository, new ObjectMapper());
        var request = new FeedbackController.FeedbackRequest("Where is the policy?", "See [1].",
                List.of(new FeedbackController.Source(1, "policy.pdf", "3", "Returns can be requested.")), AnswerFeedback.Rating.UP);

        var receipt = controller.record(collectionId, request);
        assertNotNull(receipt.id());
        var stored = org.mockito.ArgumentCaptor.forClass(AnswerFeedback.class);
        verify(repository).save(stored.capture());
        assertEquals("Where is the policy?", stored.getValue().getQuestion());
        assertTrue(stored.getValue().getSourcesJson().contains("policy.pdf"));
        assertEquals(AnswerFeedback.Rating.UP, stored.getValue().getRating());
    }
}
