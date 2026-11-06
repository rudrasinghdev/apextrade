package com.rudra.apextrade.ingress.exception;

import com.rudra.apextrade.web.error.ApexTradeException;
import org.springframework.http.HttpStatus;

public class EngineUnavailableException extends ApexTradeException {
    public EngineUnavailableException(String message) {
        super(message, HttpStatus.SERVICE_UNAVAILABLE, "ENGINE_UNAVAILABLE");
    }
}

