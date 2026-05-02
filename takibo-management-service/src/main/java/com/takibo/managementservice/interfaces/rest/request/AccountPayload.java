package com.takibo.managementservice.interfaces.rest.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record AccountPayload(
        @Email @NotBlank String email,
        @NotBlank String password
) {}
