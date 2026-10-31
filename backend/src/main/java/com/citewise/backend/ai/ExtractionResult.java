package com.citewise.backend.ai;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

public record ExtractionResult(
        @JsonProperty("page_count") int pageCount,
        List<ExtractedPage> pages
) {
    public record ExtractedPage(
            @JsonProperty("page_number") int pageNumber,
            String text,
            @JsonProperty("char_count") int charCount,
            @JsonProperty("needs_ocr") boolean needsOcr,
            String method
    ) {}
}