package com.takibo.identitycore.application.rbac.governance.command;

import java.util.UUID;

/** {@code reason} est une justification d'audit, jamais un attribut persisté. */
public record AssignUserRoleCommand(
        UUID userId,
        String roleCode,
        String reason
) {}
