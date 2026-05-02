package com.takibo.managementservice.domain.exception;

public class PublicClientMustNotHaveSecretException extends RuntimeException {
  public PublicClientMustNotHaveSecretException() {
    super("PUBLIC clients must not require a client secret");
  }
}