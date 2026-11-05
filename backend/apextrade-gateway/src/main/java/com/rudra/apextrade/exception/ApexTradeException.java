package com.rudra.apextrade.exception;

import org.springframework.http.HttpStatus;

/**
 * Base abstract exception for all ApexTrade domain and trading errors.
 * Encapsulates the corresponding HTTP status and machine-readable error code.
 */
public abstract class ApexTradeException extends RuntimeException {

    private final HttpStatus status;
    private final String errorCode;

    protected ApexTradeException(String message, HttpStatus status, String errorCode) {
        super(message);
        this.status = status;
        this.errorCode = errorCode;
    }

    protected ApexTradeException(String message, HttpStatus status, String errorCode, Throwable cause) {
        super(message, cause);
        this.status = status;
        this.errorCode = errorCode;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getErrorCode() {
        return errorCode;
    }
}
