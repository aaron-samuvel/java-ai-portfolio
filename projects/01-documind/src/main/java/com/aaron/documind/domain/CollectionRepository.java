package com.aaron.documind.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface CollectionRepository extends JpaRepository<DocumentCollection, UUID> {
}

