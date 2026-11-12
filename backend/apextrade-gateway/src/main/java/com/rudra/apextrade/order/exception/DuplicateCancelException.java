package com.rudra.apextrade.order.exception;

import com.rudra.apextrade.web.error.ApexTradeException;
import org.springframework.http.HttpStatus;

public class DuplicateCancelException extends ApexTradeException {
    public DuplicateCancelException(String message) {
        super(message, HttpStatus.CONFLICT, "CANCEL_ALREADY_PENDING");
    }
}

