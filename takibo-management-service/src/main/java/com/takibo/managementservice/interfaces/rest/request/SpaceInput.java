package com.takibo.managementservice.interfaces.rest.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SpaceInput(
        @NotBlank(message = "space.code is required")
        @Size(min = 2, max = 80)
        String code,

        @NotBlank(message = "space.name is required")
        @Size(max = 80)
        String name,

        @Size(max = 255)
        String description
) {}
