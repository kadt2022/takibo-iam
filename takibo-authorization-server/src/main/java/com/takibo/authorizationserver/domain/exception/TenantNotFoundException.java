package com.takibo.authorizationserver.domain.exception;

/**
 * Exception thrown when tenant cannot be found for given client_id.
 * Typically maps to OAuth2 error "invalid_client" (401).
 */
public class TenantNotFoundException extends RuntimeException {
    
    public TenantNotFoundException(String message) {
        super(message);
    }
    
    public TenantNotFoundException(String message, Throwable cause) {
        super(message, cause);
    }
}
