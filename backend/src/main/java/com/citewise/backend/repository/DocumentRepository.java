package com.citewise.backend.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.citewise.backend.entity.Document;

public interface DocumentRepository extends JpaRepository<Document, Long> {

    List<Document> findByUserIdOrderByCreatedAtDesc(Long userId);

    Optional<Document> findByIdAndUserId(Long id, Long userId);
}