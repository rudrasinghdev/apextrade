package com.rudra.apextrade.security;

public record UserPrincipal(
        long userId,
        String email,
        String role
) { }

