package com.rudra.apextrade.account.dto;

public record AccountBalanceResponse(
        long userId,
        String availableUsd,
        String reservedUsd,
        String totalUsd,
        String availableBtc,
        String reservedBtc,
        String totalBtc
) {}

