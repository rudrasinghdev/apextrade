package com.rudra.apextrade.user.dto;

public record UserProfileResponse(
        long userId,
        String email,
        long createdAt
) {}

