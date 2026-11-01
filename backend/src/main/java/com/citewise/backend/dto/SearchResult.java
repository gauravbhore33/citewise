package com.citewise.backend.dto;

public record SearchResult(
        long chunkId,
        long documentId,
        String documentName,
        int pageNumber,
        int chunkIndex,
        double similarity,
        double distance,
        String text
) {}