package com.takibo.managementservice.interfaces.rest.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record OrganizationInput(
        UUID id,

        @NotBlank(message = "organization.code is required")
        @Size(min = 6, max = 80)
        String code,

        @Size(min = 2, max = 160)
        @NotBlank(message = "organization.name is required")
        String name
) {
}