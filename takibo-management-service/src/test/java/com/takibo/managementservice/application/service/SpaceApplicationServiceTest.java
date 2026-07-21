package com.takibo.managementservice.application.service;

import com.takibo.managementservice.application.command.CreateSpaceCommand;
import com.takibo.managementservice.domain.model.ActorSource;
import com.takibo.managementservice.domain.model.Space;
import com.takibo.managementservice.domain.model.SpaceRegistrationResult;
import com.takibo.managementservice.domain.vo.SpaceId;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SpaceApplicationServiceTest {

    @Test
    void createSpace_maps_the_domain_registration_to_an_application_result() {
        UUID organizationId = UUID.fromString("aaaaaaaa-0000-0000-0000-000000000001");
        UUID spaceId = UUID.fromString("bbbbbbbb-0000-0000-0000-000000000002");
        UUID ownerId = UUID.fromString("cccccccc-0000-0000-0000-000000000003");
        Instant now = Instant.parse("2026-07-20T12:00:00Z");
        CreateSpaceCommand command = CreateSpaceCommand.builder()
                .orgId(organizationId)
                .ownerAccountId(ownerId)
                .source(ActorSource.HUMAN)
                .code("finance")
                .name("Finance")
                .description("Finance workspace")
                .build();
        Space space = Space.createNew(
                SpaceId.of(spaceId), organizationId, ownerId,
                "finance", "Finance", "Finance workspace", now);
        SpaceRegistrationOrchestrator orchestrator = mock(SpaceRegistrationOrchestrator.class);
        when(orchestrator.registerSpace(command)).thenReturn(new SpaceRegistrationResult(space));

        var result = new SpaceApplicationService(orchestrator).createSpace(command);

        assertThat(result.id()).isEqualTo(spaceId);
        assertThat(result.orgId()).isEqualTo(organizationId);
        assertThat(result.ownerAccountId()).isEqualTo(ownerId);
        assertThat(result.code()).isEqualTo("finance");
        assertThat(result.statusUpdatedAt()).isEqualTo(now);
        assertThat(result.createdAt()).isEqualTo(now);
    }

    @Test
    void createSpace_rejects_a_null_command() {
        assertThatThrownBy(() -> new SpaceApplicationService(mock(SpaceRegistrationOrchestrator.class))
                .createSpace(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("CreateSpaceCommand");
    }
}
