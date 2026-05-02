package com.takibo.identitycore.domain.model;

public record UserRegistrationResult(User user, EmailAddress accountEmail) {
    public UserRegistrationResult {
        if (user == null) throw new IllegalArgumentException("user cannot be null");
        if (accountEmail == null) throw new IllegalArgumentException("accountEmail cannot be null");
    }
}
