package com.takibo.managementservice.domain.exception;

public class PublicAuthorizationCodeRequiresPkceException extends RuntimeException {
  public PublicAuthorizationCodeRequiresPkceException() {
    super("PUBLIC clients using authorization_code must enable PKCE");
  }
}