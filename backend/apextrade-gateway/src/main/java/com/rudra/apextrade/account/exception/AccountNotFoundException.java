package com.rudra.apextrade.account.exception;

import com.rudra.apextrade.web.error.ApexTradeException;
import org.springframework.http.HttpStatus;

public class AccountNotFoundException extends ApexTradeException {
    public AccountNotFoundException(String message) {
        super(message, HttpStatus.NOT_FOUND, "ACCOUNT_NOT_FOUND");
    }
}

