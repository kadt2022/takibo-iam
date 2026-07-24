package com.takibo.managementservice.application.service;

import com.takibo.managementservice.application.command.CreateSpaceCommand;
import com.takibo.managementservice.application.port.OrganizationReadPort;
import com.takibo.managementservice.application.port.SpaceEventPublisherPort;
import com.takibo.managementservice.domain.event.SpaceCreatedEvent;
import com.takibo.managementservice.domain.model.ActorSource;
import com.takibo.managementservice.domain.model.OrganizationContext;
import com.takibo.managementservice.domain.repository.SpaceRepository;
import com.takibo.managementservice.domain.service.SpaceCreationDomainService;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SpaceRegistrationOrchestratorTest {

    private static final UUID ORGANIZATION_ID = UUID.fromString(
            "aaaaaaaa-0000-0000-0000-000000000001"
    );
    private static final UUID OWNER_ACCOUNT_ID = UUID.fromString(
            "bbbbbbbb-0000-0000-0000-000000000002"
    );
    private static final Instant NOW =
            Instant.parse("2026-07-23T12:00:00Z");

    @Test
    void selects_the_first_available_generated_code_and_publishes_the_event() {
        OrganizationReadPort organizationReadPort =
                mock(OrganizationReadPort.class);
        SpaceCodeGenerator spaceCodeGenerator =
                mock(SpaceCodeGenerator.class);
        SpaceRepository spaceRepository = mock(SpaceRepository.class);
        SpaceEventPublisherPort eventPublisher =
                mock(SpaceEventPublisherPort.class);

        when(organizationReadPort
                .getOrganizationContextForSpaceCreation(ORGANIZATION_ID))
                .thenReturn(new OrganizationContext(
                        ORGANIZATION_ID,
                        true,
                        2
                ));
        when(spaceCodeGenerator.generateInitialCode("Finance", "Finance"))
                .thenReturn("finance");
        when(spaceCodeGenerator.generateNextCandidate("finance"))
                .thenReturn("finance-1234");
        when(spaceRepository.existsByOrgIdAndCode(
                ORGANIZATION_ID,
                "finance"
        )).thenReturn(true);
        when(spaceRepository.existsByOrgIdAndCode(
                ORGANIZATION_ID,
                "finance-1234"
        )).thenReturn(false);
        when(spaceRepository.save(any()))
                .thenAnswer(invocation -> invocation.getArgument(0));

        SpaceRegistrationOrchestrator orchestrator =
                new SpaceRegistrationOrchestrator(
                        organizationReadPort,
                        new SpaceCreationDomainService(),
                        spaceCodeGenerator,
                        spaceRepository,
                        eventPublisher,
                        Clock.fixed(NOW, ZoneOffset.UTC)
                );

        var result = orchestrator.registerSpace(CreateSpaceCommand.builder()
                .orgId(ORGANIZATION_ID)
                .ownerAccountId(OWNER_ACCOUNT_ID)
                .source(ActorSource.HUMAN)
                .name("Finance")
                .code("Finance")
                .description("Financial operations")
                .build());

        assertThat(result.space().getCode()).isEqualTo("finance-1234");
        assertThat(result.space().getOrgId()).isEqualTo(ORGANIZATION_ID);
        assertThat(result.space().getCreatedAt()).isEqualTo(NOW);
        verify(spaceCodeGenerator).generateNextCandidate("finance");
        verify(eventPublisher).publish(any(SpaceCreatedEvent.class));
    }
}
