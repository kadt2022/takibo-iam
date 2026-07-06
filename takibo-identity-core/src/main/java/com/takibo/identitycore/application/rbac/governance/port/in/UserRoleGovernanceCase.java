package com.takibo.identitycore.application.rbac.governance.port.in;

import com.takibo.identitycore.application.rbac.governance.command.AssignUserRoleCommand;
import com.takibo.identitycore.application.rbac.governance.command.RemoveUserRoleCommand;
import com.takibo.identitycore.integration.space.port.ResolvedSpaceKey;
import com.takibo.identitycore.interfaces.rest.response.UserRoleAssignmentsResponse;

import java.util.UUID;

/**
 * Gouvernance des rôles directs d'un user situé. Toutes les opérations retournent
 * l'état courant des assignations directes : les mutations sont idempotentes.
 */
public interface UserRoleGovernanceCase {

    UserRoleAssignmentsResponse listDirectRoles(ResolvedSpaceKey key, UUID userId);

    UserRoleAssignmentsResponse assignRole(ResolvedSpaceKey key, AssignUserRoleCommand command);

    UserRoleAssignmentsResponse removeRole(ResolvedSpaceKey key, RemoveUserRoleCommand command);
}
