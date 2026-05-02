package com.takibo.managementservice.interfaces.rest.request;

import jakarta.validation.constraints.NotBlank;

public record ProfilePayload(
        @NotBlank String username,
        @NotBlank String firstName,
        @NotBlank String lastName
) {}
