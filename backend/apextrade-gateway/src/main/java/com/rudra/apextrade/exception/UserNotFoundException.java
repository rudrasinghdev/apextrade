package com.rudra.apextrade.exception;

import org.springframework.http.HttpStatus;

public class UserNotFoundException extends ApexTradeException {
    public UserNotFoundException(String message) {
        super(message, HttpStatus.NOT_FOUND, "USER_NOT_FOUND");
    }
}
