package com.rudra.apextrade.dto;

public record LoginResponse(
        String token,
        String tokenType,
        long userId,
        String username,
        String email
) {
    public LoginResponse(String token, long userId, String username, String email) {
        this(token, "Bearer", userId, username, email);
    }
}

