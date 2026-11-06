package com.rudra.apextrade.account.exception;

import com.rudra.apextrade.web.error.ApexTradeException;
import org.springframework.http.HttpStatus;

public class AccountAlreadyExistsException extends ApexTradeException {
    public AccountAlreadyExistsException(String message) {
        super(message, HttpStatus.CONFLICT, "ACCOUNT_ALREADY_EXISTS");
    }
}

