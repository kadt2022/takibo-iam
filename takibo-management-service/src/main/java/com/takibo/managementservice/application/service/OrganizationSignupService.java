package com.takibo.managementservice.application.service;

import com.takibo.identitycore.application.identity.command.CreateUserCommand;
import com.takibo.identitycore.application.identity.port.AccountApplicationCase;
import com.takibo.identitycore.application.identity.port.UserApplicationCase;
import com.takibo.identitycore.interfaces.rest.response.AccountResponse;
import com.takibo.identitycore.interfaces.rest.response.UserResponse;
import com.takibo.managementservice.application.command.CreateSpaceCommand;
import com.takibo.managementservice.application.command.CreateSpaceResult;
import com.takibo.managementservice.application.provisioning.TechnicalRbacProvision;
import com.takibo.managementservice.application.security.ActorSource;
import com.takibo.managementservice.interfaces.rest.request.OrganizationInput;
import com.takibo.managementservice.interfaces.rest.request.OrganizationSignupRequest;
import com.takibo.managementservice.interfaces.rest.response.OrganizationSignupResponse;
import com.takibo.managementservice.interfaces.rest.response.SpaceResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.Assert;

import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class OrganizationSignupService {

    private final OrganizationApplicationService orgApp;
    private final SpaceApplicationService spaceApp;
    private final AccountApplicationCase accountApp;
    private final UserApplicationCase userApp;
    private final TechnicalRbacProvision technicalRbacProvision;

    @Transactional
    public OrganizationSignupResponse signup(OrganizationSignupRequest req) {
        log.info("Starting organization signup - email={}, username={}",
                req.account().email(),
                req.profile().username());

        UUID orgId = resolveOrganization(req.organization());

        AccountResponse account = accountApp.createAccountInOrg(
                orgId,
                req.account().email(),
                req.account().password()
        );
        log.info("Account created - accountId={}", account.id());

        CreateSpaceCommand spaceCommand = CreateSpaceCommand.builder()
                .orgId(orgId)
                .ownerAccountId(account.id())
                .source(ActorSource.HUMAN)
                .name(req.space().name())
                .code(req.space().code())
                .description(req.space().description())
                .build();

        SpaceResponse space = spaceApp.createSpace(spaceCommand);

        log.info("Space created - spaceId={}", space.id());


        CreateUserCommand founderInSpace = CreateUserCommand.builder()
                .spaceId(space.id())
                .accountId(account.id())
                .email(null)
                .rawPassword(null)
                .username(req.profile().username())
                .firstName(req.profile().firstName())
                .lastName(req.profile().lastName())
                .metadata(Map.of())
                .build();

        UserResponse founder = userApp.createUser(founderInSpace);
        log.info("User created - userId={}", founder.id());

        technicalRbacProvision.provisionFounder(
                orgId,
                space.id(),
                account.id(),
                "SYSTEM"
        );
        log.info("RBAC provisioned for accountId={}", account.id());

        log.info("Organization signup completed - orgId={}, spaceId={}, accountId={}, userId={}",
                orgId, space.id(), account.id(), founder.id());

        return new OrganizationSignupResponse(
                orgId,
                space.id(),
                account.id(),
                founder.id()
        );
    }

    private UUID resolveOrganization(OrganizationInput org) {
        Assert.notNull(org, "organization is required");

        if (org.id() != null) {
            return org.id();
        }

        Assert.hasText(org.code(), "organization.code is required when id is absent");
        Assert.hasText(org.name(), "organization.name is required when id is absent");

        return orgApp.create(org.code(), org.name()).id();
    }
}