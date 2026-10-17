package com.aaron.documind.api;

import com.aaron.documind.domain.AnswerFeedback;
import com.aaron.documind.domain.AnswerFeedback.Rating;
import com.aaron.documind.domain.AnswerFeedbackRepository;
import com.aaron.documind.domain.CollectionRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/collections/{collectionId}/feedback")
public class FeedbackController {
    private final CollectionRepository collections;
    private final AnswerFeedbackRepository feedback;
    private final ObjectMapper objectMapper;

    public FeedbackController(CollectionRepository collections, AnswerFeedbackRepository feedback, ObjectMapper objectMapper) {
        this.collections = collections;
        this.feedback = feedback;
        this.objectMapper = objectMapper;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public FeedbackReceipt record(@PathVariable UUID collectionId, @Valid @RequestBody FeedbackRequest request) {
        if (!collections.existsById(collectionId)) throw new IllegalArgumentException("Collection was not found.");
        try {
            String sourceJson = objectMapper.writeValueAsString(request.sources());
            AnswerFeedback saved = feedback.save(new AnswerFeedback(collectionId, request.question(), request.answer(), sourceJson, request.rating()));
            return new FeedbackReceipt(saved.getId(), saved.getRating(), saved.getCreatedAt());
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Source records could not be saved.", exception);
        }
    }

    public record FeedbackRequest(@NotBlank @Size(max = 4_000) String question,
                                  @NotBlank @Size(max = 20_000) String answer,
                                  @NotNull @Size(max = 20) List<@Valid Source> sources,
                                  @NotNull Rating rating) { }
    public record Source(@NotNull Integer n, @NotBlank @Size(max = 255) String fileName,
                         @Size(max = 120) String page, @NotBlank @Size(max = 240) String snippet) { }
    public record FeedbackReceipt(UUID id, Rating rating, Instant createdAt) { }
}
