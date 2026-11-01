package com.citewise.backend.service;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.server.ResponseStatusException;

import com.citewise.backend.ai.AiServiceClient;
import com.citewise.backend.dto.SearchResult;
import com.citewise.backend.repository.ChunkRepository;

@Service
public class SearchService {

    private static final int DEFAULT_TOP_K = 5;

    private final AiServiceClient aiServiceClient;
    private final ChunkRepository chunkRepository;

    public SearchService(AiServiceClient aiServiceClient, ChunkRepository chunkRepository) {
        this.aiServiceClient = aiServiceClient;
        this.chunkRepository = chunkRepository;
    }

    public List<SearchResult> search(Long userId, String question, Integer topK) {
        int k = topK == null ? DEFAULT_TOP_K : topK;

        List<Double> queryVector;
        try {
            queryVector = aiServiceClient.embed(List.of(question.trim()), "query").get(0);
        } catch (RestClientException e) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Search is temporarily unavailable");
        }

        return chunkRepository.search(userId, queryVector, k).stream()
                .map(hit -> new SearchResult(
                        hit.chunkId(), hit.documentId(), hit.documentName(),
                        hit.pageNumber(), hit.chunkIndex(),
                        1.0 - hit.distance(), hit.distance(), hit.content()))
                .toList();
    }
}