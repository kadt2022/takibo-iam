package com.takibo.managementservice.domain.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.CONFLICT)
public class ClientAlreadyExistsException extends RuntimeException {
    public ClientAlreadyExistsException(String clientId) {
        super("Client with client_id '" + clientId + "' already exists");
    }

    public ClientAlreadyExistsException(String clientId, Throwable cause) {
        super("Client with client_id '" + clientId + "' already exists", cause);
    }
}
