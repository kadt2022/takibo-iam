package com.takibo.identitycore.application.rbac.governance.port.in;

import com.takibo.identitycore.application.rbac.governance.command.AddUserToGroupCommand;
import com.takibo.identitycore.application.rbac.governance.command.RemoveUserFromGroupCommand;
import com.takibo.identitycore.integration.space.port.ResolvedSpaceKey;
import com.takibo.identitycore.interfaces.rest.response.UserGroupMembershipsResponse;

import java.util.UUID;

/**
 * Gouvernance des memberships directs d'un user situé. Toutes les opérations
 * retournent l'état courant : les mutations sont idempotentes.
 */
public interface UserGroupGovernanceCase {

    UserGroupMembershipsResponse listDirectGroups(ResolvedSpaceKey key, UUID userId);

    UserGroupMembershipsResponse addToGroup(ResolvedSpaceKey key, AddUserToGroupCommand command);

    UserGroupMembershipsResponse removeFromGroup(ResolvedSpaceKey key, RemoveUserFromGroupCommand command);
}
