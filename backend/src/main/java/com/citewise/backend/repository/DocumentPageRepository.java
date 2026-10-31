package com.citewise.backend.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.citewise.backend.entity.DocumentPage;

public interface DocumentPageRepository extends JpaRepository<DocumentPage, Long> {

    List<DocumentPage> findByDocumentIdOrderByPageNumber(Long documentId);

    void deleteByDocumentId(Long documentId);
}