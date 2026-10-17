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
@Table(name = "documents")
public class StoredDocument {

    @Id
    private UUID id;

    @Column(name = "collection_id", nullable = false)
    private UUID collectionId;

    @Column(name = "file_name", nullable = false, length = 255)
    private String fileName;

    @Column(name = "content_type", nullable = false, length = 120)
    private String contentType;

    @Column(nullable = false, length = 64)
    private String sha256;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private DocumentStatus status;

    @Column(name = "storage_key", nullable = false, length = 80)
    private String storageKey;

    @Column(name = "error_message", length = 500)
    private String errorMessage;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected StoredDocument() {
    }

    public StoredDocument(UUID id, UUID collectionId, String fileName, String contentType,
                          String sha256, String storageKey) {
        this.id = id;
        this.collectionId = collectionId;
        this.fileName = fileName;
        this.contentType = contentType;
        this.sha256 = sha256;
        this.storageKey = storageKey;
        this.status = DocumentStatus.PROCESSING;
        this.createdAt = Instant.now();
    }

    public void markIndexed() {
        this.status = DocumentStatus.INDEXED;
        this.errorMessage = null;
    }

    public void markFailed(String message) {
        this.status = DocumentStatus.FAILED;
        this.errorMessage = message;
    }

    public UUID getId() { return id; }
    public UUID getCollectionId() { return collectionId; }
    public String getFileName() { return fileName; }
    public String getContentType() { return contentType; }
    public String getSha256() { return sha256; }
    public DocumentStatus getStatus() { return status; }
    public String getStorageKey() { return storageKey; }
    public String getErrorMessage() { return errorMessage; }
    public Instant getCreatedAt() { return createdAt; }
}

