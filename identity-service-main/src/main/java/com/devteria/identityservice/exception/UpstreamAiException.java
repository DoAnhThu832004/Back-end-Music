package com.devteria.identityservice.exception;

public class UpstreamAiException extends RuntimeException {
    public UpstreamAiException(String message) {
        super(message);
    }

    public UpstreamAiException(String message, Throwable cause) {
        super(message, cause);
    }
}
