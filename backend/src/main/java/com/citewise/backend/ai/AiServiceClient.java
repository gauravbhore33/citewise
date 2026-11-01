package com.citewise.backend.ai;

import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

import com.fasterxml.jackson.annotation.JsonProperty;

@Component
public class AiServiceClient {

    public static final int EMBEDDING_DIMENSION = 384;
    private static final int EMBED_BATCH_SIZE = 32;
    private static final int MAX_TEXT_CHARS = 8000;

    public record PageIn(@JsonProperty("page_number") int pageNumber, String text) {}
    public record ChunkRequest(List<PageIn> pages) {}
    public record EmbedRequest(List<String> texts, String kind) {}

    private final RestClient restClient;

    public AiServiceClient(@Value("${app.ai-service.url}") String baseUrl,
                           @Value("${app.ai-service.connect-timeout-seconds}") long connectSeconds,
                           @Value("${app.ai-service.read-timeout-seconds}") long readSeconds) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(connectSeconds));
        factory.setReadTimeout(Duration.ofSeconds(readSeconds));

        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(factory)
                .build();
    }

    /** Sends the file to the Python service and returns the text of each page. */
    public ExtractionResult extract(Path file) {
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("file", new FileSystemResource(file));

        return restClient.post()
                .uri("/extract")
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(body)
                .retrieve()
                .body(ExtractionResult.class);
    }

    /** Splits the pages into overlapping chunks. */
    public ChunkResult chunk(List<ExtractionResult.ExtractedPage> pages) {
        List<PageIn> pageInputs = pages.stream()
                .map(p -> new PageIn(p.pageNumber(), p.text()))
                .toList();

        return restClient.post()
                .uri("/chunk")
                .contentType(MediaType.APPLICATION_JSON)
                .body(new ChunkRequest(pageInputs))
                .retrieve()
                .body(ChunkResult.class);
    }

    /** Turns texts into vectors, a batch at a time. kind is "passage" for stored text, "query" for a question. */
    public List<List<Double>> embed(List<String> texts, String kind) {
        List<List<Double>> all = new ArrayList<>(texts.size());

        for (int start = 0; start < texts.size(); start += EMBED_BATCH_SIZE) {
            List<String> batch = texts.subList(start, Math.min(start + EMBED_BATCH_SIZE, texts.size()))
                    .stream()
                    .map(t -> t.length() > MAX_TEXT_CHARS ? t.substring(0, MAX_TEXT_CHARS) : t)
                    .toList();

            EmbedResult result = restClient.post()
                    .uri("/embed")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(new EmbedRequest(batch, kind))
                    .retrieve()
                    .body(EmbedResult.class);

            if (result == null || result.embeddings() == null || result.embeddings().size() != batch.size()) {
                throw new IllegalStateException("The embedding service gave an unexpected answer");
            }
            for (List<Double> vector : result.embeddings()) {
                if (vector.size() != EMBEDDING_DIMENSION) {
                    throw new IllegalStateException("Unexpected vector size " + vector.size());
                }
            }
            all.addAll(result.embeddings());
        }
        return all;
    }
}