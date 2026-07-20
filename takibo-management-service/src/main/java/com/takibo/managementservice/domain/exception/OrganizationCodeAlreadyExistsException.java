package com.takibo.managementservice.domain.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.CONFLICT)
public class OrganizationCodeAlreadyExistsException extends RuntimeException {
  public OrganizationCodeAlreadyExistsException(String code) {
    super("Organization code already exists: " + code);
  }

  public OrganizationCodeAlreadyExistsException(String code, Throwable cause) {
    super("Organization code already exists: " + code, cause);
  }
}
