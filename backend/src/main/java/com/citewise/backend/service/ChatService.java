package com.citewise.backend.service;

import com.citewise.backend.ai.AiServiceClient;
import com.citewise.backend.dto.ChatResponse;
import com.citewise.backend.dto.ChatSource;
import com.citewise.backend.dto.SearchResult;
import com.citewise.backend.repository.ChatRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class ChatService {

    private static final Logger log = LoggerFactory.getLogger(ChatService.class);

    public static final String NOT_FOUND_TEXT = "I couldn't find this in your documents.";
    private static final int MAX_CONTEXT_CHARS = 6000;
    private static final int MAX_NAME_CHARS = 300;

    private final SearchService searchService;
    private final AiServiceClient aiServiceClient;
    private final ChatRepository chatRepository;
    private final double minSimilarity;
    private final int defaultTopK;

    public ChatService(SearchService searchService,
                       AiServiceClient aiServiceClient,
                       ChatRepository chatRepository,
                       @Value("${app.rag.min-similarity}") double minSimilarity,
                       @Value("${app.rag.top-k}") int defaultTopK) {
        this.searchService = searchService;
        this.aiServiceClient = aiServiceClient;
        this.chatRepository = chatRepository;
        this.minSimilarity = minSimilarity;
        this.defaultTopK = defaultTopK;
    }

    public ChatResponse ask(Long userId, String question, Integer topK) {
        String q = question.trim();
        int k = topK == null ? defaultTopK : topK;

        // 1. retrieve the closest chunks from this user's own documents
        List<SearchResult> results = searchService.search(userId, q, k);
        Double top = results.isEmpty() ? null : results.get(0).similarity();

        // 2. layer 1: nothing similar enough, so refuse without calling the model
        if (top == null || top < minSimilarity) {
            return notFound(userId, q, "LOW_SIMILARITY", top, null);
        }

        // 3. ask the model, giving it numbered passages
        List<AiServiceClient.AnswerContext> contexts = new ArrayList<>();
        for (int i = 0; i < results.size(); i++) {
            SearchResult r = results.get(i);
            contexts.add(new AiServiceClient.AnswerContext(i + 1, cut(r.documentName(), MAX_NAME_CHARS),
                    r.pageNumber(), cut(r.text(), MAX_CONTEXT_CHARS)));
        }

        AiServiceClient.AnswerResult result;
        try {
            result = aiServiceClient.answer(q, contexts);
        } catch (RestClientResponseException | ResourceAccessException e) {
            log.warn("Answer service call failed: {}", e.getMessage());
            chatRepository.saveMessage(userId, q, null, "ERROR", null, top, null, List.of());
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "The answer service is temporarily unavailable. Please try again.");
        }
        if (result == null || (!result.notFound() && (result.answer() == null || result.answer().isBlank()))) {
            chatRepository.saveMessage(userId, q, null, "ERROR", null, top, null, List.of());
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "The answer service gave an unusable reply.");
        }

        // 4. layer 2: the model says the passages do not contain the answer
        if (result.notFound()) {
            return notFound(userId, q, "NOT_IN_CONTEXT", top, result.model());
        }

        // 5. answered: save the answer and a snapshot of every source, marking the ones it cited
        Set<Integer> cited = new HashSet<>(result.cited() == null ? List.of() : result.cited());
        List<ChatRepository.SourceRow> rows = new ArrayList<>();
        for (int i = 0; i < results.size(); i++) {
            SearchResult r = results.get(i);
            rows.add(new ChatRepository.SourceRow(i + 1, r.documentId(), r.documentName(), r.pageNumber(),
                    r.similarity(), cited.contains(i + 1), r.text()));
        }
        long id = chatRepository.saveMessage(userId, q, result.answer(), "ANSWERED", null, top, result.model(), rows);
        return new ChatResponse(id, q, result.answer(), "ANSWERED", null, top, !cited.isEmpty(),
                toSources(rows), Instant.now());
    }

    public List<ChatResponse> history(Long userId, int limit) {
        int safeLimit = Math.max(1, Math.min(limit, 50));
        List<ChatRepository.MessageRow> messages = chatRepository.history(userId, safeLimit);
        Map<Long, List<ChatRepository.SourceRow>> sources =
                chatRepository.sourcesFor(messages.stream().map(ChatRepository.MessageRow::id).toList());

        return messages.stream().map(m -> {
            List<ChatRepository.SourceRow> rows = sources.getOrDefault(m.id(), List.of());
            boolean notFound = "NOT_FOUND".equals(m.status());
            return new ChatResponse(m.id(), m.question(), notFound ? NOT_FOUND_TEXT : m.answer(), m.status(),
                    m.reason(), m.topSimilarity(), rows.stream().anyMatch(ChatRepository.SourceRow::cited),
                    toSources(rows), m.createdAt());
        }).toList();
    }

    private ChatResponse notFound(Long userId, String question, String reason, Double top, String model) {
        long id = chatRepository.saveMessage(userId, question, null, "NOT_FOUND", reason, top, model, List.of());
        return new ChatResponse(id, question, NOT_FOUND_TEXT, "NOT_FOUND", reason, top, false, List.of(), Instant.now());
    }

    private static List<ChatSource> toSources(List<ChatRepository.SourceRow> rows) {
        return rows.stream()
                .map(r -> new ChatSource(r.n(), r.documentId(), r.documentName(), r.pageNumber(),
                        r.similarity(), r.cited(), r.content()))
                .toList();
    }

    private static String cut(String text, int max) {
        return text.length() > max ? text.substring(0, max) : text;
    }
}