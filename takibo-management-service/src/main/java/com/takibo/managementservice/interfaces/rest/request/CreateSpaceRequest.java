package com.takibo.managementservice.interfaces.rest.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateSpaceRequest(
    @NotBlank(message = "Space name is required")
    @Size(max = 80, message = "Space name must not exceed 80 characters")
    String name,

    @Size(max = 80, message = "Space code must not exceed 80 characters")
    String code,

    @Size(max = 255, message = "Space description must not exceed 255 characters")
    String description
) {}
