package com.takibo.managementservice.integration;

import com.takibo.identitycore.domain.status.SpaceOperationalStatus;
import com.takibo.managementservice.application.service.SpaceStatusService;
import com.takibo.managementservice.domain.model.SpaceStatus;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SpaceStatusCheckerCaseAdapterTest {

    private final SpaceStatusService statusService = mock(SpaceStatusService.class);
    private final SpaceStatusCheckerCaseAdapter adapter =
            new SpaceStatusCheckerCaseAdapter(statusService, new SpaceStatusMapper());

    @Test
    void translatesTheManagementStatusForTheIdentityCore() {
        UUID spaceId = UUID.randomUUID();
        when(statusService.findStatus(spaceId)).thenReturn(Optional.of(SpaceStatus.SUSPENDED));

        assertThat(adapter.findStatus(spaceId)).contains(SpaceOperationalStatus.SUSPENDED);
    }

    @Test
    void aKnownStatusIsServedFromTheShortLivedCache() {
        UUID spaceId = UUID.randomUUID();
        when(statusService.findStatus(spaceId)).thenReturn(Optional.of(SpaceStatus.ACTIVE));

        adapter.findStatus(spaceId);
        Optional<SpaceOperationalStatus> second = adapter.findStatus(spaceId);

        assertThat(second).contains(SpaceOperationalStatus.ACTIVE);
        verify(statusService, times(1)).findStatus(spaceId);
    }

    @Test
    void anUnknownSpaceIsNeverCachedSoItsCreationIsSeenImmediately() {
        UUID spaceId = UUID.randomUUID();
        when(statusService.findStatus(spaceId))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(SpaceStatus.ACTIVE));

        assertThat(adapter.findStatus(spaceId)).isEmpty();
        assertThat(adapter.findStatus(spaceId)).contains(SpaceOperationalStatus.ACTIVE);
        verify(statusService, times(2)).findStatus(spaceId);
    }

    @Test
    void theCacheIsKeyedBySpace() {
        UUID active = UUID.randomUUID();
        UUID disabled = UUID.randomUUID();
        when(statusService.findStatus(active)).thenReturn(Optional.of(SpaceStatus.ACTIVE));
        when(statusService.findStatus(disabled)).thenReturn(Optional.of(SpaceStatus.DISABLED));

        assertThat(adapter.findStatus(active)).contains(SpaceOperationalStatus.ACTIVE);
        assertThat(adapter.findStatus(disabled)).contains(SpaceOperationalStatus.DISABLED);
        assertThat(adapter.findStatus(active)).contains(SpaceOperationalStatus.ACTIVE);
    }
}
