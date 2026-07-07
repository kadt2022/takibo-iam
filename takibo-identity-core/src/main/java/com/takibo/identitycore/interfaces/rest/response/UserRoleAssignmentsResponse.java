package com.takibo.identitycore.interfaces.rest.response;

import java.util.List;
import java.util.UUID;

/**
 * État courant des rôles directs d'un user. Les mutations (assign/remove)
 * retournent le même état : idempotence observable par l'appelant.
 */
public record UserRoleAssignmentsResponse(
        UUID userId,
        List<UserRoleAssignmentResponse> roles
) {}
