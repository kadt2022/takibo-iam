package com.takibo.managementservice.domain.exception;

public class InvalidScopeException extends RuntimeException {
  public InvalidScopeException(java.util.Set<String> rejected) {
    super("Invalid scope(s): " + rejected);
  }
}