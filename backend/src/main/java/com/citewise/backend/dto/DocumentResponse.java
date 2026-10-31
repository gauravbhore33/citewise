package com.citewise.backend.dto;

import java.time.Instant;

import com.citewise.backend.entity.Document;
import com.citewise.backend.entity.DocumentStatus;

public record DocumentResponse(
        Long id,
        String originalFilename,
        String contentType,
        long fileSize,
        DocumentStatus status,
        String errorMessage,
        Instant createdAt
) {
    public static DocumentResponse from(Document d) {
        return new DocumentResponse(d.getId(), d.getOriginalFilename(), d.getContentType(),
                d.getFileSize(), d.getStatus(), d.getErrorMessage(), d.getCreatedAt());
    }
}