package com.takibo.authorizationserver.domain.exception;

/**
 * Domain exception for internal server errors (500).
 *
 * Used when tenant resolution or other system operations fail due to:
 * - TMS communication failure
 * - Database connection issues
 * - Unexpected system errors
 *
 * Maps to HTTP 500 and SentinelErrorCode.TENANT_RESOLUTION_FAILED
 */
public class TakiboServerErrorException extends RuntimeException {

    private final String errorCode;

    public TakiboServerErrorException(String message, String errorCode) {
        super(message);
        this.errorCode = errorCode;
    }

    public TakiboServerErrorException(String message) {
        this(message, "INTERNAL_ERROR");
    }

    public String getErrorCode() {
        return errorCode;
    }

    /**
     * Exception thrown when tenant resolution fails due to system error.
     * Typically maps to OAuth2 error "server_error" (500).
     */
    public static class TenantResolutionException extends RuntimeException {

        public TenantResolutionException(String message) {
            super(message);
        }

        public TenantResolutionException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
