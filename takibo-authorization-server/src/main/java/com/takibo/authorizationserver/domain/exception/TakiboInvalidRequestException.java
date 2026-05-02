package com.takibo.authorizationserver.domain.exception;

/**
 * Exception for OAuth2 "invalid_request" error (400).
 * Maps to Takibo error code OAUTH2_INVALID_REQUEST.
 */
public class TakiboInvalidRequestException extends RuntimeException {

    private final String errorCode;

    public TakiboInvalidRequestException(String message) {
        super(message);
        this.errorCode = "OAUTH2_INVALID_REQUEST";
    }

    public TakiboInvalidRequestException(String message, String errorCode) {
        super(message);
        this.errorCode = errorCode;
    }

    public String getErrorCode() {
        return errorCode;
    }
}
