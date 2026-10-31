package com.citewise.backend.service;

import java.util.List;

import org.springframework.core.task.TaskRejectedException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import com.citewise.backend.dto.DocumentResponse;
import com.citewise.backend.dto.PageResponse;
import com.citewise.backend.entity.Document;
import com.citewise.backend.entity.DocumentStatus;
import com.citewise.backend.repository.DocumentPageRepository;
import com.citewise.backend.repository.DocumentRepository;
import com.citewise.backend.repository.UserRepository;
import com.citewise.backend.storage.FileStorageService;
import com.citewise.backend.storage.StoredFile;

@Service
public class DocumentService {

    private final DocumentRepository documentRepository;
    private final DocumentPageRepository pageRepository;
    private final UserRepository userRepository;
    private final FileStorageService fileStorageService;
    private final DocumentProcessingService processingService;

    public DocumentService(DocumentRepository documentRepository,
                           DocumentPageRepository pageRepository,
                           UserRepository userRepository,
                           FileStorageService fileStorageService,
                           DocumentProcessingService processingService) {
        this.documentRepository = documentRepository;
        this.pageRepository = pageRepository;
        this.userRepository = userRepository;
        this.fileStorageService = fileStorageService;
        this.processingService = processingService;
    }

    public DocumentResponse upload(Long userId, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "No file was sent");
        }

        StoredFile stored = fileStorageService.store(file);
        Document saved;
        try {
            Document document = new Document(
                    userRepository.getReferenceById(userId),
                    cleanName(file.getOriginalFilename()),
                    stored.storedFilename(),
                    stored.type().getContentType(),
                    stored.size());
            saved = documentRepository.save(document);
        } catch (RuntimeException e) {
            fileStorageService.delete(stored.storedFilename());   // no orphan files
            throw e;
        }

        startProcessing(saved);
        return DocumentResponse.from(saved);
    }

    public List<DocumentResponse> list(Long userId) {
        return documentRepository.findByUserIdOrderByCreatedAtDesc(userId)
                .stream()
                .map(DocumentResponse::from)
                .toList();
    }

    public DocumentResponse get(Long userId, Long documentId) {
        return DocumentResponse.from(findOwned(userId, documentId));
    }

    public List<PageResponse> getPages(Long userId, Long documentId) {
        Document document = findOwned(userId, documentId);
        return pageRepository.findByDocumentIdOrderByPageNumber(document.getId())
                .stream()
                .map(PageResponse::from)
                .toList();
    }

    /** Run text extraction again (for failed documents, or ones uploaded before extraction existed). */
    public DocumentResponse reprocess(Long userId, Long documentId) {
        Document document = findOwned(userId, documentId);
        if (document.getStatus() == DocumentStatus.PROCESSING) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "This document is already being processed");
        }
        document.setStatus(DocumentStatus.UPLOADED);
        document.setErrorMessage(null);
        Document saved = documentRepository.save(document);

        startProcessing(saved);
        return DocumentResponse.from(saved);
    }

    private Document findOwned(Long userId, Long documentId) {
        return documentRepository.findByIdAndUserId(documentId, userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Document not found"));
    }

    private void startProcessing(Document document) {
        try {
            processingService.process(document.getId());
        } catch (TaskRejectedException e) {
            document.setStatus(DocumentStatus.FAILED);
            document.setErrorMessage("The server is busy. Please try again in a moment.");
            documentRepository.save(document);
        }
    }

    /** The original name is only for display. It never decides where a file is stored. */
    private String cleanName(String name) {
        if (name == null || name.isBlank()) return "unnamed";
        String justName = name.substring(Math.max(name.lastIndexOf('/'), name.lastIndexOf('\\')) + 1);
        justName = justName.replaceAll("\\p{Cntrl}", "").trim();
        if (justName.isEmpty()) return "unnamed";
        return justName.length() > 255 ? justName.substring(0, 255) : justName;
    }
}