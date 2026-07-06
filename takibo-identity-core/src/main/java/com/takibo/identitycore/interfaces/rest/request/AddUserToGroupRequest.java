package com.takibo.identitycore.interfaces.rest.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** {@code reason} est une justification d'audit — jamais persistée sur le membership. */
public record AddUserToGroupRequest(
        @NotBlank @Size(max = 120) String groupCode,
        @Size(max = 255) String reason
) {}
