package com.rudra.apextrade.account.dto;

public record AccountBalanceResponse(
        long userId,
        String availableUsdt,
        String reservedUsdt,
        String totalUsdt,
        String availableBtc,
        String reservedBtc,
        String totalBtc
) {}

