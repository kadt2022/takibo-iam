package com.takibo.audit.api;


public class AuditStoreException extends RuntimeException {
    public AuditStoreException(String message, Throwable cause) {
        super(message, cause);
    }
}