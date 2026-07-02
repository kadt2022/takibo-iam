package com.takibo.identitycore.application.identity.service;

import com.takibo.identitycore.application.identity.command.CreateUserCommand;
import com.takibo.identitycore.application.identity.mapper.UserMapper;
import com.takibo.identitycore.application.identity.port.UserApplicationCase;
import com.takibo.identitycore.domain.model.UserRegistrationResult;
import com.takibo.identitycore.integration.security.port.CurrentOrganizationContextCase;
import com.takibo.identitycore.integration.security.port.CurrentSpaceContextCase;
import com.takibo.identitycore.integration.space.port.SpaceOwnershipGuardCase;
import com.takibo.identitycore.interfaces.rest.response.UserResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.Assert;

import java.util.UUID;

@Service
@Transactional
@RequiredArgsConstructor
public class UserApplicationService implements UserApplicationCase {

    private final UserRegistrationOrchestrator userRegistrationOrchestrator;
    private final UserMapper userMapper;
    private final SpaceOwnershipGuardCase spaceOwnershipGuard;
    private final CurrentOrganizationContextCase currentOrganizationContext;
    private final CurrentSpaceContextCase currentSpaceContext;

    @Override
    public UserResponse createUser(CreateUserCommand command) {
        Assert.notNull(command, "User command must not be null");

        UUID currentOrgId = currentOrganizationContext.requireCurrentOrganizationId();
        spaceOwnershipGuard.assertSpaceBelongsToOrg(command.spaceId(), currentOrgId);

        // Un token SPACE reste limité au space qu'il porte — même R_ORG_OWNER ne bypass pas.
        UUID currentSpaceId = currentSpaceContext.requireCurrentSpaceId();
        if (!currentSpaceId.equals(command.spaceId())) {
            throw new AccessDeniedException("SPACE_CONTEXT_MISMATCH");
        }

        UserRegistrationResult result = userRegistrationOrchestrator.registerUser(command);
        return userMapper.toUserResponse(result.user(), result.accountEmail());
    }
}
