package com.takibo.identitycore.interfaces.rest.response;

import java.util.List;
import java.util.UUID;

/**
 * État courant des memberships directs d'un user. Les mutations (add/remove)
 * retournent le même état : idempotence observable par l'appelant.
 */
public record UserGroupMembershipsResponse(
        UUID userId,
        List<UserGroupMembershipResponse> groups
) {}
