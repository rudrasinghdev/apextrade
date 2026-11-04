package com.rudra.apextrade.dto;

public record RegisterResponse(
        long userId,
        String username,
        String email,
        String token
) {
}

