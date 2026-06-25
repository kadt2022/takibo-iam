package com.takibo.identitycore.application.identity.service;

import com.takibo.identitycore.application.identity.command.CreateUserCommand;
import com.takibo.identitycore.application.identity.command.ProvisionFounderUserCommand;
import com.takibo.identitycore.application.identity.mapper.UserMapper;
import com.takibo.identitycore.application.identity.port.FounderUserProvisioningCase;
import com.takibo.identitycore.domain.model.SpaceContext;
import com.takibo.identitycore.domain.model.UserRegistrationResult;
import com.takibo.identitycore.integration.space.SpaceContextVerifier;
import com.takibo.identitycore.interfaces.rest.response.UserResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

/**
 * Provisionne le fondateur d'une organisation pendant le bootstrap.
 * <p>
 * Appelle directement {@link UserRegistrationOrchestrator} (qui ne porte pas de garde
 * liée au token), au lieu de passer par {@code UserApplicationService.createUser} qui
 * vérifie l'organisation courante. La seule frontière vérifiée ici est la cohérence
 * <em>donnée</em> : le space fourni appartient bien à l'org qu'on est en train de créer.
 */
@Service
@RequiredArgsConstructor
public class FounderUserProvisioningService implements FounderUserProvisioningCase {

    private final SpaceContextVerifier spaceContextVerifier;
    private final UserRegistrationOrchestrator userRegistrationOrchestrator;
    private final UserMapper userMapper;

    @Override
    @Transactional
    public UserResponse provisionFounder(ProvisionFounderUserCommand command) {
        SpaceContext spaceContext = spaceContextVerifier.validateSpaceContext(command.spaceId());

        if (!spaceContext.organizationId().equals(command.organizationId())) {
            throw new AccessDeniedException("SPACE_ORG_MISMATCH");
        }

        CreateUserCommand createUserCommand = CreateUserCommand.builder()
                .spaceId(command.spaceId())
                .accountId(command.accountId())
                .username(command.username())
                .firstName(command.firstName())
                .lastName(command.lastName())
                .metadata(Map.of("provisioning", "organization-signup-founder"))
                .build();

        UserRegistrationResult result = userRegistrationOrchestrator.registerUser(createUserCommand);
        return userMapper.toUserResponse(result.user(), result.accountEmail());
    }
}
