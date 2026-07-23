package com.takibo.managementservice.domain.exception;

public class OAuthClientSecretRotationConflictException extends RuntimeException {
    public OAuthClientSecretRotationConflictException() {
        super("client secret rotation conflict");
    }
}
