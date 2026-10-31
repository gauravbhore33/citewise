package com.citewise.backend.dto;

import com.citewise.backend.entity.DocumentPage;

public record PageResponse(int pageNumber, String method, int charCount, String text) {

    public static PageResponse from(DocumentPage p) {
        return new PageResponse(p.getPageNumber(), p.getMethod().name(), p.getCharCount(), p.getContent());
    }
}