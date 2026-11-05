package com.rudra.apextrade.dto;

public record LoginResponse(
        String email,
        long userId,
        String token
) { }

