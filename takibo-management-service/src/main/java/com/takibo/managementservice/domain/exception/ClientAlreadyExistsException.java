package com.takibo.managementservice.domain.exception;

public class ClientAlreadyExistsException extends RuntimeException {
    public ClientAlreadyExistsException(String clientId) {
        super("Client with client_id '" + clientId + "' already exists");
    }

    public ClientAlreadyExistsException(String clientId, Throwable cause) {
        super("Client with client_id '" + clientId + "' already exists", cause);
    }
}
