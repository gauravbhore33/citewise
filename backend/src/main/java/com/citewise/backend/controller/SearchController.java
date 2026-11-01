package com.citewise.backend.controller;

import java.util.List;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.citewise.backend.dto.SearchRequest;
import com.citewise.backend.dto.SearchResult;
import com.citewise.backend.security.UserPrincipal;
import com.citewise.backend.service.SearchService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/search")
public class SearchController {

    private final SearchService searchService;

    public SearchController(SearchService searchService) {
        this.searchService = searchService;
    }

    @PostMapping
    public List<SearchResult> search(@AuthenticationPrincipal UserPrincipal principal,
                                     @Valid @RequestBody SearchRequest request) {
        return searchService.search(principal.getId(), request.question(), request.topK());
    }
}