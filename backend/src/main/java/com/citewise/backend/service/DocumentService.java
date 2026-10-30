package com.citewise.backend.service;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import com.citewise.backend.dto.DocumentResponse;
import com.citewise.backend.entity.Document;
import com.citewise.backend.repository.DocumentRepository;
import com.citewise.backend.repository.UserRepository;
import com.citewise.backend.storage.FileStorageService;
import com.citewise.backend.storage.StoredFile;

@Service
public class DocumentService {

    private final DocumentRepository documentRepository;
    private final UserRepository userRepository;
    private final FileStorageService fileStorageService;

    public DocumentService(DocumentRepository documentRepository,
                           UserRepository userRepository,
                           FileStorageService fileStorageService) {
        this.documentRepository = documentRepository;
        this.userRepository = userRepository;
        this.fileStorageService = fileStorageService;
    }

    public DocumentResponse upload(Long userId, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "No file was sent");
        }

        StoredFile stored = fileStorageService.store(file);
        try {
            Document document = new Document(
                    userRepository.getReferenceById(userId),
                    cleanName(file.getOriginalFilename()),
                    stored.storedFilename(),
                    stored.type().getContentType(),
                    stored.size());
            return DocumentResponse.from(documentRepository.save(document));
        } catch (RuntimeException e) {
            fileStorageService.delete(stored.storedFilename());   // no orphan files
            throw e;
        }
    }

    public List<DocumentResponse> list(Long userId) {
        return documentRepository.findByUserIdOrderByCreatedAtDesc(userId)
                .stream()
                .map(DocumentResponse::from)
                .toList();
    }

    public DocumentResponse get(Long userId, Long documentId) {
        return documentRepository.findByIdAndUserId(documentId, userId)
                .map(DocumentResponse::from)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Document not found"));
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