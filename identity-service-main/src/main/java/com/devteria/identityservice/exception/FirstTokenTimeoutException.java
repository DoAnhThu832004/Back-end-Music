package com.devteria.identityservice.exception;

public class FirstTokenTimeoutException extends UpstreamAiException {
    public FirstTokenTimeoutException(String message) {
        super(message);
    }

    public FirstTokenTimeoutException(String message, Throwable cause) {
        super(message, cause);
    }
}
