package com.rudra.apextrade.user.dto;

public record UserProfileResponse(
        String email,
        long userId,
        long createdAt
) {}

