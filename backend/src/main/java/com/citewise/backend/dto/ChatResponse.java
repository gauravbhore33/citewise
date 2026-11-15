package com.citewise.backend.dto;

import java.time.Instant;
import java.util.List;

public record ChatResponse(
        long id,
        String question,
        String answer,
        String status,          // ANSWERED or NOT_FOUND
        String reason,          // for NOT_FOUND: LOW_SIMILARITY or NOT_IN_CONTEXT
        Double topSimilarity,
        boolean hasCitations,
        List<ChatSource> sources,
        Instant createdAt
) {}