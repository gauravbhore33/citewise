package com.citewise.backend.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.citewise.backend.dto.DocumentResponse;
import com.citewise.backend.dto.PageResponse;
import com.citewise.backend.security.UserPrincipal;
import com.citewise.backend.service.DocumentService;

@RestController
@RequestMapping("/api/documents")
public class DocumentController {

    private final DocumentService documentService;

    public DocumentController(DocumentService documentService) {
        this.documentService = documentService;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public DocumentResponse upload(@AuthenticationPrincipal UserPrincipal principal,
                                   @RequestParam("file") MultipartFile file) {
        return documentService.upload(principal.getId(), file);
    }

    @GetMapping
    public List<DocumentResponse> list(@AuthenticationPrincipal UserPrincipal principal) {
        return documentService.list(principal.getId());
    }

    @GetMapping("/{id}")
    public DocumentResponse get(@AuthenticationPrincipal UserPrincipal principal,
                                @PathVariable Long id) {
        return documentService.get(principal.getId(), id);
    }

    @GetMapping("/{id}/pages")
    public List<PageResponse> pages(@AuthenticationPrincipal UserPrincipal principal,
                                    @PathVariable Long id) {
        return documentService.getPages(principal.getId(), id);
    }

    @PostMapping("/{id}/reprocess")
    public DocumentResponse reprocess(@AuthenticationPrincipal UserPrincipal principal,
                                      @PathVariable Long id) {
        return documentService.reprocess(principal.getId(), id);
    }
}