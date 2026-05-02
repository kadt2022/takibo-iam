package com.takibo.managementservice.domain.exception;

public class InvalidPostLogoutRedirectUriException extends RuntimeException {
  public InvalidPostLogoutRedirectUriException(java.util.Set<String> rejected) {
    super("Invalid post_logout_redirect_uri(s): " + rejected);
  }
}
