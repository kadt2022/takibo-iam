package com.takibo.managementservice.domain.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.CONFLICT)
public class OAuthClientSecretRotationConflictException extends RuntimeException {
    public OAuthClientSecretRotationConflictException() {
        super("client secret rotation conflict");
    }
}
