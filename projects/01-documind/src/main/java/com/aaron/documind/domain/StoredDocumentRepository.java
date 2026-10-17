package com.aaron.documind.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface StoredDocumentRepository extends JpaRepository<StoredDocument, UUID> {
    boolean existsByCollectionIdAndSha256(UUID collectionId, String sha256);
    Optional<StoredDocument> findByCollectionIdAndSha256(UUID collectionId, String sha256);
    List<StoredDocument> findAllByCollectionIdOrderByCreatedAtDesc(UUID collectionId);
}

