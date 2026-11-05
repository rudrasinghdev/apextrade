package com.rudra.apextrade.exception;

import org.springframework.http.HttpStatus;

public class InsufficientFundsException extends ApexTradeException {
    public InsufficientFundsException(String message) {
        super(message, HttpStatus.BAD_REQUEST, "INSUFFICIENT_FUNDS");
    }
}

