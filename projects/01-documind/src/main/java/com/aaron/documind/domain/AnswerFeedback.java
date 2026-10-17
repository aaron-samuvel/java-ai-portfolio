package com.aaron.documind.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "answer_feedback")
public class AnswerFeedback {
    @Id
    private UUID id;

    @Column(name = "collection_id", nullable = false)
    private UUID collectionId;

    @Column(nullable = false, columnDefinition = "text")
    private String question;

    @Column(nullable = false, columnDefinition = "text")
    private String answer;

    @Column(name = "sources_json", nullable = false, columnDefinition = "text")
    private String sourcesJson;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 12)
    private Rating rating;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected AnswerFeedback() { }

    public AnswerFeedback(UUID collectionId, String question, String answer, String sourcesJson, Rating rating) {
        this.id = UUID.randomUUID();
        this.collectionId = collectionId;
        this.question = question;
        this.answer = answer;
        this.sourcesJson = sourcesJson;
        this.rating = rating;
        this.createdAt = Instant.now();
    }

    public UUID getId() { return id; }
    public UUID getCollectionId() { return collectionId; }
    public String getQuestion() { return question; }
    public String getAnswer() { return answer; }
    public String getSourcesJson() { return sourcesJson; }
    public Rating getRating() { return rating; }
    public Instant getCreatedAt() { return createdAt; }

    public enum Rating { UP, DOWN }
}
