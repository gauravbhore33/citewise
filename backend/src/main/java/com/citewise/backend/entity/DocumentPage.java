package com.citewise.backend.entity;

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
@Table(name = "document_pages")
public class DocumentPage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "document_id", nullable = false)
    private Document document;

    @Column(name = "page_number", nullable = false)
    private int pageNumber;

    @Column(nullable = false, columnDefinition = "text")
    private String content;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private PageMethod method;

    @Column(name = "char_count", nullable = false)
    private int charCount;

    protected DocumentPage() {
        // required by JPA
    }

    public DocumentPage(Document document, int pageNumber, String content,
                        PageMethod method, int charCount) {
        this.document = document;
        this.pageNumber = pageNumber;
        this.content = content;
        this.method = method;
        this.charCount = charCount;
    }

    public Long getId() { return id; }
    public Document getDocument() { return document; }
    public int getPageNumber() { return pageNumber; }
    public String getContent() { return content; }
    public PageMethod getMethod() { return method; }
    public int getCharCount() { return charCount; }
}