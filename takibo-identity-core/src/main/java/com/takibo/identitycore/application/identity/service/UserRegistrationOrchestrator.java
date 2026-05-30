package com.takibo.identitycore.application.identity.service;

import com.takibo.identitycore.application.identity.command.CreateUserCommand;
import com.takibo.identitycore.domain.model.Account;
import com.takibo.identitycore.domain.model.SpaceContext;
import com.takibo.identitycore.domain.model.User;
import com.takibo.identitycore.domain.model.UserRegistrationResult;
import com.takibo.identitycore.domain.repository.UserRepository;
import com.takibo.identitycore.domain.service.AccountDomaineService;
import com.takibo.identitycore.integration.space.SpaceContextVerifier;
import com.takibo.identitycore.domain.service.UserDomainService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class UserRegistrationOrchestrator {

    private final SpaceContextVerifier spaceContextVerifier;
    private final UserDomainService userDomainService;
    private final AccountDomaineService accountDomaineService;
    private final BusinessRoleAssignmentService businessRoleAssignmentService;
  //  private final RbacAssignmentService rbacAssignmentService;
    private final UserRepository userRepository;

    public UserRegistrationResult registerUser(CreateUserCommand command) {
        // 1) Contexte
        SpaceContext spaceContext = spaceContextVerifier.validateSpaceContext(command.spaceId());

        // 2) Compte
        Account account = accountDomaineService.resolveAccountForRegistration(command, spaceContext.organizationId());

        // 3) User (construction + règles)
        User user = userDomainService.createNativeUser(
                command,
                spaceContext.organizationId(),
                spaceContext.spaceId(),
                account.getId()
        );

        // 4) Persistance
        User saved = userRepository.save(user);

        // 5) RBAC
        businessRoleAssignmentService.assignBusinessRoles(
                spaceContext.organizationId(),
                spaceContext.spaceId().value(),
                account.getId().getValue(),
                command.businessRoleCodes()
        );
      //  rbacAssignmentService.assignDefaultPermissions(spaceContext.spaceId(), saved.getId(), command);

        return new UserRegistrationResult(saved, account.getEmail());
    }
}
