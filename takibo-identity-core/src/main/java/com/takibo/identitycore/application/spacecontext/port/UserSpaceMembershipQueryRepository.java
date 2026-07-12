package com.takibo.identitycore.application.spacecontext.port;

import com.takibo.identitycore.application.spacecontext.model.UserSpaceMembership;

import java.util.List;
import java.util.UUID;

public interface UserSpaceMembershipQueryRepository {

    List<UserSpaceMembership> findByOrganizationAndAccount(UUID organizationId, UUID accountId);
}
