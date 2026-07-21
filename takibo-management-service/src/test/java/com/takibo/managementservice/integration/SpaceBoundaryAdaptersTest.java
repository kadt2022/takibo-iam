package com.takibo.managementservice.integration;

import com.takibo.identitycore.domain.status.SpaceOperationalStatus;
import com.takibo.identitycore.domain.vo.SpaceId;
import com.takibo.managementservice.application.port.SpaceLookupPort;
import com.takibo.managementservice.application.service.SpaceQueryService;
import com.takibo.managementservice.domain.model.SpaceStatus;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SpaceBoundaryAdaptersTest {

    private static final UUID SPACE_ID = UUID.fromString("bbbbbbbb-0000-0000-0000-000000000002");
    private static final UUID ORGANIZATION_ID = UUID.fromString("aaaaaaaa-0000-0000-0000-000000000001");

    @Test
    void management_adapter_crosses_the_identity_boundary_through_the_application_service() {
        SpaceLookupPort lookup = mock(SpaceLookupPort.class);
        when(lookup.existsById(SPACE_ID)).thenReturn(true);
        when(lookup.findOrganizationId(SPACE_ID)).thenReturn(Optional.of(ORGANIZATION_ID));
        SpaceManagementTmsAdapter adapter =
                new SpaceManagementTmsAdapter(new SpaceQueryService(lookup));
        SpaceId identitySpaceId = new SpaceId(SPACE_ID);

        assertThat(adapter.doesSpaceExist(identitySpaceId)).isTrue();
        assertThat(adapter.findOrgIdBySpaceId(identitySpaceId)).contains(ORGANIZATION_ID);
    }

    @Test
    void status_mapper_translates_equivalent_boundary_values() {
        SpaceStatusMapper mapper = new SpaceStatusMapper();

        assertThat(mapper.toCoreStatus(SpaceStatus.ACTIVE)).isEqualTo(SpaceOperationalStatus.ACTIVE);
        assertThat(mapper.toCoreStatus(SpaceStatus.SUSPENDED)).isEqualTo(SpaceOperationalStatus.SUSPENDED);
    }
}
