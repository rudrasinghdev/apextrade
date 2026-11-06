package com.rudra.apextrade.auth.security;

public record UserPrincipal(
        long userId,
        String email
) {}

