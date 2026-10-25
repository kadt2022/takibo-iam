package com.takibo.securitycontext.exception;

public class TakiboSecurityContextException extends RuntimeException {

    public TakiboSecurityContextException(String message) {
        super(message);
    }

    public TakiboSecurityContextException(String message, Throwable cause) {
        super(message, cause);
    }
}
