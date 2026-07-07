package com.takibo.identitycore.interfaces.rest.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** {@code reason} est une justification d'audit — jamais persistée sur l'assignation. */
public record AssignUserRoleRequest(
        @NotBlank @Size(max = 120) String roleCode,
        @Size(max = 255) String reason
) {}
