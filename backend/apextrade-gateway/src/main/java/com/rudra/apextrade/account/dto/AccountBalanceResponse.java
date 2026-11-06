package com.rudra.apextrade.account.dto;

public record AccountBalanceResponse(
        long userId,
        long availableUsd,
        long reservedUsd,
        long totalUsd,
        long availableBtc,
        long reservedBtc,
        long totalBtc
) {}

