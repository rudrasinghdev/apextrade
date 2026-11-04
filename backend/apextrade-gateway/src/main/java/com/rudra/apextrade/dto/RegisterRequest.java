package com.rudra.apextrade.dto;

public record RegisterRequest(
        String username,
        String email,
        String password
) {
}

