package com.app.transfer.exception;

public class ProviderApiException extends RuntimeException {
    public ProviderApiException(String message) {
        super(message);
    }

    public ProviderApiException(String message, Throwable cause) {
        super(message, cause);
    }
}