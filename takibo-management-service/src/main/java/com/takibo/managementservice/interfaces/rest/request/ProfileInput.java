package com.takibo.managementservice.interfaces.rest.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ProfileInput(
        @NotBlank(message = "profile.username is required")
        @Size(min = 2, max = 150)
        String username,

        @NotBlank(message = "profile.firstName is required")
        @Size(max = 160)
        String firstName,

        @NotBlank(message = "profile.lastName is required")
        @Size(max = 160)
        String lastName
) {}
