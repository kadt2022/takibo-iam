package com.takibo.managementservice.application.command;

import java.util.UUID;

public record OrganizationSignupCommand(
        Organization organization,
        Space space,
        Account account,
        Profile profile
) {
    public record Organization(UUID id, String code, String name) {}

    public record Space(String code, String name, String description) {}

    public record Account(String email, String password) {}

    public record Profile(String username, String firstName, String lastName) {}
}
