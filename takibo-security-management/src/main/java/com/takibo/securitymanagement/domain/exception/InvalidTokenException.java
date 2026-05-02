package com.takibo.securitymanagement.domain.exception;

/**
 * Exception levée quand le JWT est invalide, expiré, ou absent
 * → HTTP 401 Unauthorized
 */
public class InvalidTokenException extends RuntimeException {
    
    public InvalidTokenException(String message) {
        super(message);
    }
    
    public InvalidTokenException(String message, Throwable cause) {
        super(message, cause);
    }
}