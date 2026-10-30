package com.citewise.backend.dto;

public record AuthResponse(
        String token,
        String tokenType,
        long expiresInMs,
        String name,
        String email
) {}