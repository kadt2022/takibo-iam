package com.takibo.managementservice.application.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.takibo.managementservice.application.command.CreateSpaceCommand;
import com.takibo.managementservice.domain.event.SpaceCreatedEvent;
import com.takibo.managementservice.domain.exception.SpaceCodeAlreadyExistsException;
import com.takibo.managementservice.domain.model.OrganizationContext;
import com.takibo.managementservice.domain.model.ActorSource;
import com.takibo.managementservice.domain.model.Space;
import com.takibo.managementservice.domain.model.SpaceRegistrationResult;
import com.takibo.managementservice.domain.repository.SpaceRepository;
import com.takibo.managementservice.domain.service.SpaceCreationDomainService;
import com.takibo.managementservice.domain.vo.SpaceId;
import com.takibo.outbox.core.model.OutboxEnvelope;
import com.takibo.outbox.core.port.OutboxPublisher;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class SpaceRegistrationOrchestrator {

    private static final int MAX_CODE_ATTEMPTS = 5;

    private final OrganizationDomainService organizationDomainService;
    private final SpaceCreationDomainService spaceCreationDomainService;
    private final SpaceCodeGenerator spaceCodeGenerator;
    private final SpaceRepository spaceRepository;

    private final OutboxPublisher outboxPublisher;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    private static final Logger log = LoggerFactory.getLogger(SpaceRegistrationOrchestrator.class);

    @Transactional
    public SpaceRegistrationResult registerSpace(CreateSpaceCommand command) {
        log.info("registerSpace() reached | orgId={} code={}", command.orgId(), command.code());
        OrganizationContext orgCtx =
                organizationDomainService.assertOrganizationAllowsSpaceCreation(command.orgId());

        String candidate = spaceCodeGenerator.normalizeOrGenerate(command.code(), command.name());

        for (int attempt = 1; attempt <= MAX_CODE_ATTEMPTS; attempt++) {

            if (spaceRepository.existsByOrgIdAndCode(orgCtx.orgId(), candidate)) {
                candidate = spaceCodeGenerator.nextCandidate(candidate);
                continue;
            }

            SpaceId spaceId = SpaceId.of(UUID.randomUUID());
            Space space = spaceCreationDomainService.createSpace(
                    orgCtx,
                    command.ownerAccountId(),
                    candidate,
                    command.name(),
                    command.description(),
                    spaceId,
                    clock.instant()
            );

            Space saved = spaceRepository.save(space);

            ActorSource actorSource = command.source() != null ? command.source() : ActorSource.SYSTEM;


            SpaceCreatedEvent event = new SpaceCreatedEvent(
                    UUID.randomUUID(),
                    saved.getOrgId(),
                    saved.getId().value(),
                    command.ownerAccountId(),
                    actorSource,
                    clock.instant()
            );

            publishSpaceCreated(event, saved.getOrgId(), saved.getId().value());

            return new SpaceRegistrationResult(saved);
        }

        throw new SpaceCodeAlreadyExistsException(command.code());
    }

    private void publishSpaceCreated(SpaceCreatedEvent event, UUID orgId, UUID spaceId) {
        String payloadJson = toJson(event);

        outboxPublisher.publish(
                OutboxEnvelope.of(
                        "SPACE_CREATED",
                        "SPACE",
                        spaceId.toString(),
                        orgId,
                        spaceId,
                        payloadJson,
                        "SPACE:CREATED:" + spaceId
                )
        );
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Unable to serialize outbox payload", e);
        }
    }
}
