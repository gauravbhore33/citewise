package com.citewise.backend.dto;

public record ChatSource(
        int n,
        Long documentId,
        String documentName,
        int pageNumber,
        double similarity,
        boolean cited,
        String text
) {}