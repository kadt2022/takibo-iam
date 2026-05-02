package com.takibo.identitycore.domain.exception;

public class LastOwnerException extends RuntimeException {

    public LastOwnerException(String message) {
        super(message);
    }

    public LastOwnerException(String message, Throwable cause) {
        super(message, cause);
    }
}
