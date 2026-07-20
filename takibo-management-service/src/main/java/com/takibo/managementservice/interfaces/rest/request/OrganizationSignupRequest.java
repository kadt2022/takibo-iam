package com.takibo.managementservice.interfaces.rest.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

public record OrganizationSignupRequest(
        @NotNull @Valid OrganizationInput organization,
        @NotNull @Valid SpaceInput space,
        @NotNull @Valid AccountInput account,
        @NotNull @Valid ProfileInput profile
) {
}
