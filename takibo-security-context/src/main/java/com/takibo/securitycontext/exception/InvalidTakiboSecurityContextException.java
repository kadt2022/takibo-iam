package com.takibo.securitycontext.exception;

public class InvalidTakiboSecurityContextException extends TakiboSecurityContextException {

    public InvalidTakiboSecurityContextException(String message) {
        super(message);
    }

    public InvalidTakiboSecurityContextException(String message, Throwable cause) {
        super(message, cause);
    }
}
