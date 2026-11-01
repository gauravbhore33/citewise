package com.citewise.backend.service;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientResponseException;

import com.citewise.backend.ai.AiServiceClient;
import com.citewise.backend.ai.ChunkResult;
import com.citewise.backend.ai.ExtractionResult;
import com.citewise.backend.entity.Document;
import com.citewise.backend.entity.DocumentPage;
import com.citewise.backend.entity.DocumentStatus;
import com.citewise.backend.entity.PageMethod;
import com.citewise.backend.repository.ChunkRepository;
import com.citewise.backend.repository.DocumentPageRepository;
import com.citewise.backend.repository.DocumentRepository;
import com.citewise.backend.storage.FileStorageService;

@Service
public class DocumentProcessingService {

    private static final Logger log = LoggerFactory.getLogger(DocumentProcessingService.class);

    private final DocumentRepository documentRepository;
    private final DocumentPageRepository pageRepository;
    private final ChunkRepository chunkRepository;
    private final FileStorageService fileStorageService;
    private final AiServiceClient aiServiceClient;
    private final TransactionTemplate transactionTemplate;

    public DocumentProcessingService(DocumentRepository documentRepository,
                                     DocumentPageRepository pageRepository,
                                     ChunkRepository chunkRepository,
                                     FileStorageService fileStorageService,
                                     AiServiceClient aiServiceClient,
                                     PlatformTransactionManager transactionManager) {
        this.documentRepository = documentRepository;
        this.pageRepository = pageRepository;
        this.chunkRepository = chunkRepository;
        this.fileStorageService = fileStorageService;
        this.aiServiceClient = aiServiceClient;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    /** Runs on a background thread: extract text, chunk it, embed the chunks, store everything. */
    @Async("documentExecutor")
    public void process(Long documentId) {
        Document document = documentRepository.findById(documentId).orElse(null);
        if (document == null) {
            log.warn("Document {} no longer exists, skipping", documentId);
            return;
        }

        updateStatus(documentId, DocumentStatus.PROCESSING, null);
        log.info("Processing document {}", documentId);

        try {
            // 1. extract the text (slow, no transaction open)
            Path file = fileStorageService.resolve(document.getStoredFilename());
            ExtractionResult extraction = aiServiceClient.extract(file);

            if (extraction == null || extraction.pages() == null) {
                updateStatus(documentId, DocumentStatus.FAILED, "The text extraction service gave an unexpected answer");
                return;
            }
            int totalChars = extraction.pages().stream().mapToInt(ExtractionResult.ExtractedPage::charCount).sum();
            if (totalChars == 0) {
                updateStatus(documentId, DocumentStatus.FAILED, "No readable text was found in this file");
                return;
            }
            savePages(documentId, extraction.pages());

            // 2. split the pages into chunks
            ChunkResult chunked = aiServiceClient.chunk(extraction.pages());
            if (chunked == null || chunked.chunks() == null || chunked.chunks().isEmpty()) {
                updateStatus(documentId, DocumentStatus.FAILED, "No searchable text was found in this file");
                return;
            }

            // 3. turn every chunk into a vector
            List<String> texts = chunked.chunks().stream().map(ChunkResult.Chunk::text).toList();
            List<List<Double>> vectors = aiServiceClient.embed(texts, "passage");

            // 4. store the chunks and vectors, and mark the document READY, in one transaction
            List<ChunkRepository.NewChunk> rows = new ArrayList<>(texts.size());
            for (int i = 0; i < texts.size(); i++) {
                ChunkResult.Chunk chunk = chunked.chunks().get(i);
                rows.add(new ChunkRepository.NewChunk(chunk.pageNumber(), chunk.chunkIndex(), chunk.text(), vectors.get(i)));
            }
            storeChunksAndFinish(documentId, rows);
            log.info("Document {} is READY: {} pages, {} chunks", documentId, extraction.pages().size(), rows.size());

        } catch (RestClientResponseException e) {
            log.warn("AI service answered {} for document {}", e.getStatusCode().value(), documentId);
            int status = e.getStatusCode().value();
            String message = switch (status) {
                case 422 -> "The file could not be read";
                case 503 -> "Text extraction is temporarily unavailable";
                default -> "Processing failed (code " + status + ")";
            };
            updateStatus(documentId, DocumentStatus.FAILED, message);
        } catch (ResourceAccessException e) {
            log.warn("AI service not reachable for document {}: {}", documentId, e.getMessage());
            updateStatus(documentId, DocumentStatus.FAILED, "The text extraction service is not reachable");
        } catch (Exception e) {
            log.error("Unexpected error while processing document {}", documentId, e);
            updateStatus(documentId, DocumentStatus.FAILED, "Processing failed unexpectedly");
        }
    }

    private void updateStatus(Long documentId, DocumentStatus status, String errorMessage) {
        transactionTemplate.executeWithoutResult(tx -> {
            Document document = documentRepository.findById(documentId).orElseThrow();
            document.setStatus(status);
            document.setErrorMessage(errorMessage);
        });
    }

    private void savePages(Long documentId, List<ExtractionResult.ExtractedPage> extracted) {
        transactionTemplate.executeWithoutResult(tx -> {
            Document document = documentRepository.findById(documentId).orElseThrow();

            // safe to repeat: remove old pages first, and make the delete happen before the inserts
            pageRepository.deleteByDocumentId(documentId);
            pageRepository.flush();

            List<DocumentPage> pages = extracted.stream()
                    .map(p -> new DocumentPage(document, p.pageNumber(), p.text(),
                            "ocr".equalsIgnoreCase(p.method()) ? PageMethod.OCR : PageMethod.TEXT,
                            p.charCount()))
                    .toList();
            pageRepository.saveAll(pages);
        });
    }

    private void storeChunksAndFinish(Long documentId, List<ChunkRepository.NewChunk> rows) {
        transactionTemplate.executeWithoutResult(tx -> {
            chunkRepository.replaceForDocument(documentId, rows);
            Document document = documentRepository.findById(documentId).orElseThrow();
            document.setStatus(DocumentStatus.READY);
            document.setErrorMessage(null);
        });
    }
}