package com.rudra.apextrade.order.exception;

import com.rudra.apextrade.web.error.ApexTradeException;
import org.springframework.http.HttpStatus;

public class UnauthorizedOrderAccessException extends ApexTradeException {
    public UnauthorizedOrderAccessException(String message) {
        super(message, HttpStatus.FORBIDDEN, "FORBIDDEN");
    }
}
