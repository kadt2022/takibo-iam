package com.takibo.managementservice.interfaces.rest.request;

import jakarta.validation.constraints.NotBlank;

public record SpacePayload(
        @NotBlank String name,
        String slug,
        String description
) {}
