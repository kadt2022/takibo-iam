package com.takibo.managementservice.application.service;

import com.takibo.managementservice.application.command.CreateSpaceCommand;
import com.takibo.managementservice.application.port.OrganizationReadPort;
import com.takibo.managementservice.application.port.SpaceEventPublisherPort;
import com.takibo.managementservice.domain.event.SpaceCreatedEvent;
import com.takibo.managementservice.domain.exception.SpaceCodeAlreadyExistsException;
import com.takibo.managementservice.domain.model.ActorSource;
import com.takibo.managementservice.domain.model.OrganizationContext;
import com.takibo.managementservice.domain.model.Space;
import com.takibo.managementservice.domain.model.SpaceCreationRequest;
import com.takibo.managementservice.domain.model.SpaceRegistrationResult;
import com.takibo.managementservice.domain.repository.SpaceRepository;
import com.takibo.managementservice.domain.service.SpaceCreationDomainService;
import com.takibo.managementservice.domain.vo.SpaceId;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;

@Service
@RequiredArgsConstructor
public class SpaceRegistrationOrchestrator {

    private static final int MAX_CODE_ATTEMPTS = 5;

    private final OrganizationReadPort organizationReadPort;
    private final SpaceCreationDomainService spaceCreationDomainService;
    private final SpaceCodeGenerator spaceCodeGenerator;
    private final SpaceRepository spaceRepository;

    private final SpaceEventPublisherPort spaceEventPublisherPort;
    private final Clock clock;

    private static final Logger log =
            LoggerFactory.getLogger(SpaceRegistrationOrchestrator.class);

    @Transactional
    public SpaceRegistrationResult registerSpace(CreateSpaceCommand command) {
        log.info(
                "Registering space | organizationId={} requestedCode={}",
                command.orgId(),
                command.code()
        );
        OrganizationContext organizationContext =
                organizationReadPort.getOrganizationContextForSpaceCreation(
                        command.orgId()
                );

        spaceCreationDomainService.assertEligibleForCreation(organizationContext);

        String initialCode = spaceCodeGenerator.generateInitialCode(
                command.code(),
                command.name()
        );
        String availableCode = Stream.iterate(
                        initialCode,
                        spaceCodeGenerator::generateNextCandidate
                )
                .limit(MAX_CODE_ATTEMPTS)
                .filter(candidateCode ->
                        !spaceRepository.existsByOrgIdAndCode(
                                organizationContext.orgId(),
                                candidateCode
                        )
                )
                .findFirst()
                .orElseThrow(() ->
                        new SpaceCodeAlreadyExistsException(command.code())
                );

        SpaceId spaceId = SpaceId.of(UUID.randomUUID());
        Space space = spaceCreationDomainService.createSpace(
                new SpaceCreationRequest(
                        organizationContext,
                        command.ownerAccountId(),
                        availableCode,
                        command.name(),
                        command.description(),
                        spaceId,
                        clock.instant()
                )
        );
        Space savedSpace = spaceRepository.save(space);
        ActorSource actorSource = Optional.ofNullable(command.source())
                .orElse(ActorSource.SYSTEM);

        SpaceCreatedEvent spaceCreatedEvent = new SpaceCreatedEvent(
                UUID.randomUUID(),
                savedSpace.getOrgId(),
                savedSpace.getId().value(),
                command.ownerAccountId(),
                actorSource,
                clock.instant()
        );
        spaceEventPublisherPort.publish(spaceCreatedEvent);

        return new SpaceRegistrationResult(savedSpace);
    }
}
