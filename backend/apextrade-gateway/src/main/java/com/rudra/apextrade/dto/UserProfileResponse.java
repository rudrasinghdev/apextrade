package com.rudra.apextrade.dto;

public record UserProfileResponse(
        long userId,
        String email,
        String role,
        long createdAt
) {
}

