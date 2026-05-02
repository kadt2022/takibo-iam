package com.takibo.identitycore.domain.exception;

public class DuplicateAssignmentException extends RuntimeException {

    public DuplicateAssignmentException(String message) {
        super(message);
    }

    public DuplicateAssignmentException(String message, Throwable cause) {
        super(message, cause);
    }
}
