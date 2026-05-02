package com.takibo.managementservice.domain.exception;

public class InvalidGrantTypeException extends RuntimeException {
  public InvalidGrantTypeException(java.util.Set<String> rejected) {
    super("Invalid grant type(s): " + rejected);
  }
}
