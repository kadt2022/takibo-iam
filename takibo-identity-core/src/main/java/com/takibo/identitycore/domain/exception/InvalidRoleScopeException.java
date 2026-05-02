package com.takibo.identitycore.domain.exception;

public class InvalidRoleScopeException extends RuntimeException {

    public InvalidRoleScopeException(String message) {
        super(message);
    }

    public InvalidRoleScopeException(String message, Throwable cause) {
        super(message, cause);
    }
}
