package com.citewise.backend.controller;

import java.util.List;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.citewise.backend.dto.ChatRequest;
import com.citewise.backend.dto.ChatResponse;
import com.citewise.backend.security.UserPrincipal;
import com.citewise.backend.service.ChatService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/chat")
public class ChatController {

    private final ChatService chatService;

    public ChatController(ChatService chatService) {
        this.chatService = chatService;
    }

    @PostMapping
    public ChatResponse ask(@AuthenticationPrincipal UserPrincipal principal,
                            @Valid @RequestBody ChatRequest request) {
        return chatService.ask(principal.getId(), request.question(), request.topK());
    }

    @GetMapping("/history")
    public List<ChatResponse> history(@AuthenticationPrincipal UserPrincipal principal,
                                      @RequestParam(defaultValue = "20") int limit) {
        return chatService.history(principal.getId(), limit);
    }
}