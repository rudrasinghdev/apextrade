package com.rudra.apextrade.user.exception;

import com.rudra.apextrade.web.error.ApexTradeException;
import org.springframework.http.HttpStatus;

public class UserNotFoundException extends ApexTradeException {
    public UserNotFoundException(String message) {
        super(message, HttpStatus.NOT_FOUND, "USER_NOT_FOUND");
    }
}
