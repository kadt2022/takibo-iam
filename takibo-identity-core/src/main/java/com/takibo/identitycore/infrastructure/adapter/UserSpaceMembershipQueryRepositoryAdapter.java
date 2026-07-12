package com.takibo.identitycore.infrastructure.adapter;

import com.takibo.identitycore.application.spacecontext.model.UserSpaceMembership;
import com.takibo.identitycore.application.spacecontext.port.UserSpaceMembershipQueryRepository;
import com.takibo.identitycore.infrastructure.jpa.repository.JpaUserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserSpaceMembershipQueryRepositoryAdapter implements UserSpaceMembershipQueryRepository {

    private final JpaUserRepository jpa;

    @Override
    public List<UserSpaceMembership> findByOrganizationAndAccount(UUID organizationId, UUID accountId) {
        return jpa.findSpaceMembershipsByOrgAndAccount(organizationId, accountId);
    }
}
