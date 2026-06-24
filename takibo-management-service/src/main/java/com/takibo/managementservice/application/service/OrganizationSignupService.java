package com.takibo.managementservice.application.service;

import com.takibo.identitycore.application.identity.command.ProvisionFounderUserCommand;
import com.takibo.identitycore.application.identity.port.AccountApplicationCase;
import com.takibo.identitycore.application.identity.port.FounderUserProvisioningCase;
import com.takibo.identitycore.integration.security.port.CurrentOrganizationContextCase;
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
    private final AccountApplicationCase accountApp;
    private final FounderUserProvisioningCase founderProvisioning;
    private final CurrentOrganizationContextCase currentOrganizationContext;
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


        // Provisioning fondateur : acte de bootstrap, indépendant du contexte d'org du token.
        ProvisionFounderUserCommand founderCommand = new ProvisionFounderUserCommand(
                orgId,
                space.id(),
                account.id(),
                req.profile().username(),
                req.profile().firstName(),
                req.profile().lastName()
        );

        UserResponse founder = founderProvisioning.provisionFounder(founderCommand);
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

        // Org existante : ce n'est PAS un bootstrap. On exige que l'appelant en soit propriétaire
        // (sinon n'importe quel appelant authentifié pourrait provisionner un fondateur ailleurs).
        if (org.id() != null) {
            UUID currentOrgId = currentOrganizationContext.requireCurrentOrganizationId();
            if (!org.id().equals(currentOrgId)) {
                throw new AccessDeniedException("ORG_OWNERSHIP_REQUIRED");
            }
            return org.id();
        }

        // Org absente : création d'une nouvelle frontière (self-service signup), aucun contexte requis.
        Assert.hasText(org.code(), "organization.code is required when id is absent");
        Assert.hasText(org.name(), "organization.name is required when id is absent");

        return orgApp.create(org.code(), org.name()).id();
    }
}