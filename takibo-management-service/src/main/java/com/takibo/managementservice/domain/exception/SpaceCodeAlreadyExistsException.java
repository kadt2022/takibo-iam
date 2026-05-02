package com.takibo.managementservice.domain.exception;

public class SpaceCodeAlreadyExistsException extends RuntimeException {
  public SpaceCodeAlreadyExistsException(String code) {
    super("Space code already exists: " + code);
  }
}
