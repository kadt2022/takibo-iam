package com.takibo.managementservice.domain.exception;

public class InvalidRedirectUriException extends RuntimeException {
  public InvalidRedirectUriException(java.util.Set<String> rejected) {
    super("Invalid redirect_uri(s): " + rejected);
  }
}