package com.takibo.managementservice.application.service;

import com.takibo.managementservice.application.command.CreateSpaceCommand;
import com.takibo.managementservice.application.command.OrganizationSignupCommand;
import com.takibo.managementservice.application.port.FounderProvisioningPort;
import com.takibo.managementservice.application.port.OrganizationAccountProvisioningPort;
import com.takibo.managementservice.application.port.TechnicalRbacProvisioningPort;
import com.takibo.managementservice.application.result.CreateSpaceResult;
import com.takibo.managementservice.application.result.OrganizationSignupResult;
import com.takibo.managementservice.domain.model.ActorSource;
import com.takibo.managementservice.domain.model.OrganizationSignupDecision;
import com.takibo.managementservice.domain.policy.OrganizationSignupPolicy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class OrganizationSignupService {

    private final OrganizationApplicationService organizationApplicationService;
    private final SpaceApplicationService spaceApplicationService;
    private final OrganizationAccountProvisioningPort
            organizationAccountProvisioningPort;
    private final FounderProvisioningPort founderProvisioningPort;
    private final TechnicalRbacProvisioningPort
            technicalRbacProvisioningPort;
    private final OrganizationSignupPolicy organizationSignupPolicy;

    @Transactional
    public OrganizationSignupResult signup(OrganizationSignupCommand command) {
        log.info(
                "Starting organization signup - email={}, username={}",
                command.account().email(),
                command.profile().username()
        );

        UUID organizationId =
                resolveOrganizationId(command.organization());

        UUID accountId = organizationAccountProvisioningPort.createAccount(
                organizationId,
                command.account().email(),
                command.account().password()
        );
        log.info("Account created - accountId={}", accountId);

        CreateSpaceCommand spaceCommand = CreateSpaceCommand.builder()
                .orgId(organizationId)
                .ownerAccountId(accountId)
                .source(ActorSource.HUMAN)
                .name(command.space().name())
                .code(command.space().code())
                .description(command.space().description())
                .build();

        CreateSpaceResult space =
                spaceApplicationService.createSpace(spaceCommand);
        log.info("Space created - spaceId={}", space.id());

        UUID founderId = founderProvisioningPort.provisionFounder(
                organizationId,
                space.id(),
                accountId,
                command.profile().username(),
                command.profile().firstName(),
                command.profile().lastName()
        );
        log.info("User created - userId={}", founderId);

        technicalRbacProvisioningPort.provisionFounder(
                organizationId,
                space.id(),
                accountId,
                "SYSTEM"
        );
        log.info("RBAC provisioned for accountId={}", accountId);

        log.info(
                "Organization signup completed - "
                        + "orgId={}, spaceId={}, accountId={}, userId={}",
                organizationId,
                space.id(),
                accountId,
                founderId
        );

        return new OrganizationSignupResult(
                organizationId,
                space.id(),
                accountId,
                founderId
        );
    }

    private UUID resolveOrganizationId(
            OrganizationSignupCommand.Organization organization
    ) {
        OrganizationSignupCommand.Organization requiredOrganization =
                Optional.ofNullable(organization)
                        .orElseThrow(() ->
                                new IllegalArgumentException(
                                        "organization is required"
                                )
                        );

        OrganizationSignupDecision decision =
                organizationSignupPolicy.decide(
                        requiredOrganization.id(),
                        requiredOrganization.code(),
                        requiredOrganization.name()
                );

        return switch (decision) {
            case OrganizationSignupDecision.CreateNew(
                    var code,
                    var name
            ) ->
                    organizationApplicationService
                            .create(code, name)
                            .id();
            case OrganizationSignupDecision.ExistingOrganizationForbidden
                    ignored -> throw new AccessDeniedException(
                            "EXISTING_ORGANIZATION_SIGNUP_FORBIDDEN"
                    );
        };
    }
}
