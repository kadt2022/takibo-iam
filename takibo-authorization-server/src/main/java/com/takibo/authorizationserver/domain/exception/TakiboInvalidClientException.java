package com.takibo.authorizationserver.domain.exception;

/**
 * Exception for OAuth2 "invalid_client" error (401).
 * Maps to Takibo error code OAUTH2_INVALID_CLIENT.
 */
public class TakiboInvalidClientException extends RuntimeException {

    private final String errorCode;

    public TakiboInvalidClientException(String message) {
        super(message);
        this.errorCode = "OAUTH2_INVALID_CLIENT";
    }

    public String getErrorCode() {
        return errorCode;
    }
}
