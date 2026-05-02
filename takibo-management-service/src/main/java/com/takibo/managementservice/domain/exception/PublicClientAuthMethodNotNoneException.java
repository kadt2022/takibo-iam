package com.takibo.managementservice.domain.exception;

public class PublicClientAuthMethodNotNoneException extends RuntimeException {
  public PublicClientAuthMethodNotNoneException(String method) {
    super("PUBLIC clients must use token_endpoint_auth_method=none (found: " + method + ")");
  }
}