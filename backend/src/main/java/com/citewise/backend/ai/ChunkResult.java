package com.citewise.backend.ai;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

public record ChunkResult(
        @JsonProperty("chunk_count") int chunkCount,
        List<Chunk> chunks
) {
    public record Chunk(
            @JsonProperty("chunk_index") int chunkIndex,
            @JsonProperty("page_number") int pageNumber,
            String text,
            @JsonProperty("word_count") int wordCount
    ) {}
}