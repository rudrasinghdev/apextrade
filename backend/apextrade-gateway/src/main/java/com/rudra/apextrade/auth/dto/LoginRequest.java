package com.rudra.apextrade.auth.dto;

public record LoginRequest(
        String email,
        String password
) {}

