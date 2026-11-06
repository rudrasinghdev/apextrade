package com.rudra.apextrade.user.exception;

import com.rudra.apextrade.web.error.ApexTradeException;
import org.springframework.http.HttpStatus;

public class UserAlreadyExistsException extends ApexTradeException {
    public UserAlreadyExistsException(String message) {
        super(message, HttpStatus.CONFLICT, "USER_ALREADY_EXISTS");
    }
}
