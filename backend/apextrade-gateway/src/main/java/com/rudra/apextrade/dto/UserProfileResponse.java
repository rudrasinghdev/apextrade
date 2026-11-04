package com.rudra.apextrade.dto;

public record UserProfileResponse(
        long userId,
        String username,
        String email,
        String role,
        long createdAt
) {
}

