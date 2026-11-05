package com.rudra.apextrade.exception;

import org.springframework.http.HttpStatus;

public class EngineUnavailableException extends ApexTradeException {
    public EngineUnavailableException(String message) {
        super(message, HttpStatus.SERVICE_UNAVAILABLE, "ENGINE_UNAVAILABLE");
    }
}
