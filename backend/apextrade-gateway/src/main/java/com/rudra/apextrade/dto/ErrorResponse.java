package com.rudra.apextrade.dto;

public record ErrorResponse(
        int status,
        String error,
        String message
) { }

