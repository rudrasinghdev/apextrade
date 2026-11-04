package com.rudra.apextrade.dto;

public record AuthResponse(
        String token,
        long userId,
        String username,
        String email
) {
}

