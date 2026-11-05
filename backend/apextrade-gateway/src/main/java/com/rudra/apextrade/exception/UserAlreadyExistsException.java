package com.rudra.apextrade.exception;

import org.springframework.http.HttpStatus;

public class UserAlreadyExistsException extends ApexTradeException {
    public UserAlreadyExistsException(String message) {
        super(message, HttpStatus.BAD_REQUEST, "USER_ALREADY_EXISTS");
    }
}
