package com.takibo.managementservice.application.service;

import com.takibo.managementservice.domain.model.SpaceStatus;
import com.takibo.managementservice.domain.policy.SpaceStatusTransitionPolicy;
import com.takibo.managementservice.domain.repository.SpaceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SpaceStatusApplicationServiceTest {

    @Mock
    private SpaceRepository spaceRepository;

    private SpaceStatusApplicationService service;

    @BeforeEach
    void setUp() {
        service = new SpaceStatusApplicationService(
                spaceRepository,
                new SpaceStatusTransitionPolicy()
        );
    }

    @Test
    void keeps_an_idempotent_status_without_writing() {
        UUID spaceId = UUID.randomUUID();
        when(spaceRepository.findStatusById(spaceId))
                .thenReturn(Optional.of(SpaceStatus.ACTIVE));

        assertThat(service.updateStatus(
                spaceId,
                SpaceStatus.ACTIVE,
                Optional.empty()
        )).isEqualTo(SpaceStatus.ACTIVE);

        verify(spaceRepository, never()).updateStatus(
                any(),
                any(),
                any(),
                any()
        );
    }

    @Test
    void persists_a_status_transition() {
        UUID spaceId = UUID.randomUUID();
        when(spaceRepository.findStatusById(spaceId))
                .thenReturn(Optional.of(SpaceStatus.ACTIVE));
        when(spaceRepository.updateStatus(
                org.mockito.ArgumentMatchers.eq(spaceId),
                org.mockito.ArgumentMatchers.eq(SpaceStatus.SUSPENDED),
                org.mockito.ArgumentMatchers.eq("maintenance"),
                any(Instant.class)
        )).thenReturn(1);

        assertThat(service.updateStatus(
                spaceId,
                SpaceStatus.SUSPENDED,
                Optional.of("maintenance")
        )).isEqualTo(SpaceStatus.SUSPENDED);
    }

    @Test
    void reports_a_concurrent_transition_conflict() {
        UUID spaceId = UUID.randomUUID();
        when(spaceRepository.findStatusById(spaceId))
                .thenReturn(Optional.of(SpaceStatus.ACTIVE));
        when(spaceRepository.updateStatus(
                org.mockito.ArgumentMatchers.eq(spaceId),
                org.mockito.ArgumentMatchers.eq(SpaceStatus.DISABLED),
                org.mockito.ArgumentMatchers.isNull(),
                any(Instant.class)
        )).thenReturn(0);

        assertThatThrownBy(() -> service.updateStatus(
                spaceId,
                SpaceStatus.DISABLED,
                Optional.empty()
        ))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage(
                        "Concurrent update prevented updating status for space "
                                + spaceId
                );
    }
}
