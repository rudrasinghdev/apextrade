package com.rudra.apextrade.web.error;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public abstract class ApexTradeException extends RuntimeException {

    private final HttpStatus status;
    private final String errorCode;

    protected ApexTradeException(String message, HttpStatus status, String errorCode) {
        super(message);
        this.status = status;
        this.errorCode = errorCode;
    }
}

