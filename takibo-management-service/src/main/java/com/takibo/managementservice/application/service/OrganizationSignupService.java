package com.takibo.managementservice.application.service;

import com.takibo.managementservice.application.command.CreateSpaceCommand;
import com.takibo.managementservice.application.result.CreateSpaceResult;
import com.takibo.managementservice.application.command.OrganizationSignupCommand;
import com.takibo.managementservice.application.port.FounderProvisioningPort;
import com.takibo.managementservice.application.port.OrganizationAccountProvisioningPort;
import com.takibo.managementservice.application.port.TechnicalRbacProvisioningPort;
import com.takibo.managementservice.application.result.OrganizationSignupResult;
import com.takibo.managementservice.domain.model.ActorSource;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.Assert;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class OrganizationSignupService {

    private final OrganizationApplicationService orgApp;
    private final SpaceApplicationService spaceApp;
    private final OrganizationAccountProvisioningPort accountProvisioning;
    private final FounderProvisioningPort founderProvisioning;
    private final TechnicalRbacProvisioningPort technicalRbacProvisioning;

    @Transactional
    public OrganizationSignupResult signup(OrganizationSignupCommand command) {
        log.info("Starting organization signup - email={}, username={}",
                command.account().email(),
                command.profile().username());

        UUID orgId = resolveOrganization(command.organization());

        UUID accountId = accountProvisioning.createAccount(
                orgId,
                command.account().email(),
                command.account().password()
        );
        log.info("Account created - accountId={}", accountId);

        CreateSpaceCommand spaceCommand = CreateSpaceCommand.builder()
                .orgId(orgId)
                .ownerAccountId(accountId)
                .source(ActorSource.HUMAN)
                .name(command.space().name())
                .code(command.space().code())
                .description(command.space().description())
                .build();

        CreateSpaceResult space = spaceApp.createSpace(spaceCommand);

        log.info("Space created - spaceId={}", space.id());


        // Provisioning fondateur : acte de bootstrap, indépendant du contexte d'org du token.
        UUID founderId = founderProvisioning.provisionFounder(
                orgId,
                space.id(),
                accountId,
                command.profile().username(),
                command.profile().firstName(),
                command.profile().lastName()
        );
        log.info("User created - userId={}", founderId);

        technicalRbacProvisioning.provisionFounder(
                orgId,
                space.id(),
                accountId,
                "SYSTEM"
        );
        log.info("RBAC provisioned for accountId={}", accountId);

        log.info("Organization signup completed - orgId={}, spaceId={}, accountId={}, userId={}",
                orgId, space.id(), accountId, founderId);

        return new OrganizationSignupResult(
                orgId,
                space.id(),
                accountId,
                founderId
        );
    }

    private UUID resolveOrganization(OrganizationSignupCommand.Organization org) {
        Assert.notNull(org, "organization is required");

        // Cette route crée la frontière d'organisation et son unique fondateur initial.
        // Une organisation existante doit être administrée par une route distincte avec
        // une autorisation explicite, sans jamais reprovisionner R_ORG_OWNER.
        if (org.id() != null) {
            throw new AccessDeniedException("EXISTING_ORGANIZATION_SIGNUP_FORBIDDEN");
        }

        // Org absente : création d'une nouvelle frontière (self-service signup), aucun contexte requis.
        Assert.hasText(org.code(), "organization.code is required when id is absent");
        Assert.hasText(org.name(), "organization.name is required when id is absent");

        return orgApp.create(org.code(), org.name()).id();
    }
}
