package com.takibo.managementservice.domain.exception;

public class InvalidCorsOriginException extends RuntimeException {
  public InvalidCorsOriginException(java.util.Set<String> rejected) {
    super("Invalid CORS origin(s): " + rejected);
  }
}