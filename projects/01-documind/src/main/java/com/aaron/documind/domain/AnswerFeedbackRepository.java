package com.aaron.documind.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface AnswerFeedbackRepository extends JpaRepository<AnswerFeedback, UUID> { }
