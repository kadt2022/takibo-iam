package com.takibo.managementservice.interfaces.rest.request;

import jakarta.validation.constraints.*;

public record OrganizationSignupRequest(
        @NotNull OrganizationInput organization,
        @NotNull SpaceInput space,
        @NotNull AccountInput account,
        @NotNull ProfileInput profile
) {
}