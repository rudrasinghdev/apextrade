package com.rudra.apextrade.account.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record AccountBalanceResponse(
        long userId,
        String availableUsdt,
        String reservedUsdt,
        String totalUsdt,
        String availableBtc,
        String reservedBtc,
        String totalBtc,
        @JsonProperty("availableUsd") String availableUsd,
        @JsonProperty("reservedUsd") String reservedUsd,
        @JsonProperty("totalUsd") String totalUsd
) {
    public AccountBalanceResponse(
            long userId,
            String availableUsdt,
            String reservedUsdt,
            String totalUsdt,
            String availableBtc,
            String reservedBtc,
            String totalBtc
    ) {
        this(userId, availableUsdt, reservedUsdt, totalUsdt, availableBtc, reservedBtc, totalBtc,
                availableUsdt, reservedUsdt, totalUsdt);
    }
}

