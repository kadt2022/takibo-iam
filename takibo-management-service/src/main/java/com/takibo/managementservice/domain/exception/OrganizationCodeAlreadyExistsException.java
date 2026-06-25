package com.takibo.managementservice.domain.exception;

public class OrganizationCodeAlreadyExistsException extends RuntimeException {
  public OrganizationCodeAlreadyExistsException(String code) {
    super("Organization code already exists: " + code);
  }
}
