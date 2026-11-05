package com.devteria.identityservice.exception;

public class FirstTokenTimeoutException extends RuntimeException {
    public FirstTokenTimeoutException(String message) {
        super(message);
    }

    public FirstTokenTimeoutException(String message, Throwable cause) {
        super(message, cause);
    }
}
