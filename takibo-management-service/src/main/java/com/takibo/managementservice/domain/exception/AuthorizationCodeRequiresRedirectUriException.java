package com.takibo.managementservice.domain.exception;

public class AuthorizationCodeRequiresRedirectUriException extends RuntimeException {
  public AuthorizationCodeRequiresRedirectUriException() {
    super("authorization_code grant requires at least one redirect_uri");
  }
}