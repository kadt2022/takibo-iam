package com.takibo.identitycore.domain.exception;

public class MultipleUsersMatchedException extends RuntimeException {
    public MultipleUsersMatchedException(String message) { super(message); }
}
