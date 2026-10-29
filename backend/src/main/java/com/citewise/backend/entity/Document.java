package com.citewise.backend.entity;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "documents")
public class Document {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "original_filename", nullable = false)
    private String originalFilename;

    @Column(name = "stored_filename", nullable = false, unique = true)
    private String storedFilename;

    @Column(name = "content_type", nullable = false, length = 100)
    private String contentType;

    @Column(name = "file_size", nullable = false)
    private long fileSize;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private DocumentStatus status = DocumentStatus.UPLOADED;

    @Column(name = "error_message", columnDefinition = "text")
    private String errorMessage;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private Instant createdAt;

    protected Document() {
        // required by JPA
    }

    public Document(User user, String originalFilename, String storedFilename,
                    String contentType, long fileSize) {
        this.user = user;
        this.originalFilename = originalFilename;
        this.storedFilename = storedFilename;
        this.contentType = contentType;
        this.fileSize = fileSize;
    }

    public Long getId() { return id; }
    public User getUser() { return user; }
    public String getOriginalFilename() { return originalFilename; }
    public String getStoredFilename() { return storedFilename; }
    public String getContentType() { return contentType; }
    public long getFileSize() { return fileSize; }
    public DocumentStatus getStatus() { return status; }
    public String getErrorMessage() { return errorMessage; }
    public Instant getCreatedAt() { return createdAt; }

    public void setStatus(DocumentStatus status) { this.status = status; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
}