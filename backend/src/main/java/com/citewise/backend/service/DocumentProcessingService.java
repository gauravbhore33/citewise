package com.citewise.backend.service;

import java.nio.file.Path;
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
import com.citewise.backend.ai.ExtractionResult;
import com.citewise.backend.entity.Document;
import com.citewise.backend.entity.DocumentPage;
import com.citewise.backend.entity.DocumentStatus;
import com.citewise.backend.entity.PageMethod;
import com.citewise.backend.repository.DocumentPageRepository;
import com.citewise.backend.repository.DocumentRepository;
import com.citewise.backend.storage.FileStorageService;

@Service
public class DocumentProcessingService {

    private static final Logger log = LoggerFactory.getLogger(DocumentProcessingService.class);

    private final DocumentRepository documentRepository;
    private final DocumentPageRepository pageRepository;
    private final FileStorageService fileStorageService;
    private final AiServiceClient aiServiceClient;
    private final TransactionTemplate transactionTemplate;

    public DocumentProcessingService(DocumentRepository documentRepository,
                                     DocumentPageRepository pageRepository,
                                     FileStorageService fileStorageService,
                                     AiServiceClient aiServiceClient,
                                     PlatformTransactionManager transactionManager) {
        this.documentRepository = documentRepository;
        this.pageRepository = pageRepository;
        this.fileStorageService = fileStorageService;
        this.aiServiceClient = aiServiceClient;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    /** Runs on a background thread, so the upload request does not wait for it. */
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
            Path file = fileStorageService.resolve(document.getStoredFilename());
            ExtractionResult result = aiServiceClient.extract(file);   // slow call, no transaction open

            if (result == null || result.pages() == null) {
                updateStatus(documentId, DocumentStatus.FAILED, "The text extraction service gave an unexpected answer");
                return;
            }

            int totalChars = result.pages().stream().mapToInt(ExtractionResult.ExtractedPage::charCount).sum();
            if (totalChars == 0) {
                updateStatus(documentId, DocumentStatus.FAILED, "No readable text was found in this file");
                return;
            }

            savePages(documentId, result.pages());
            log.info("Document {} is READY with {} pages", documentId, result.pages().size());

        } catch (RestClientResponseException e) {
            log.warn("AI service answered {} for document {}", e.getStatusCode().value(), documentId);
            int status = e.getStatusCode().value();
            String message = switch (status) {
                case 422 -> "The file could not be read";
                case 503 -> "Text extraction is temporarily unavailable";
                default -> "Text extraction failed (code " + status + ")";
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

            document.setStatus(DocumentStatus.READY);
            document.setErrorMessage(null);
        });
    }
}