package com.takibo.managementservice.integration;

import com.takibo.identitycore.integration.space.port.SpaceContextSummary;
import com.takibo.managementservice.domain.model.SpaceStatus;
import com.takibo.managementservice.infrastructure.entity.SpaceEntity;
import com.takibo.managementservice.infrastructure.jpa.repository.JpaSpaceRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SpaceContextCatalogTmsAdapterTest {

    private static final UUID ORG_ID = UUID.fromString("aaaaaaaa-0000-0000-0000-000000000001");
    private static final UUID FINANCE_SPACE_ID = UUID.fromString("bbbbbbbb-0000-0000-0000-000000000002");
    private static final UUID ARCHIVES_SPACE_ID = UUID.fromString("cccccccc-0000-0000-0000-000000000003");

    @Mock private JpaSpaceRepository spaces;

    @InjectMocks private SpaceContextCatalogTmsAdapter adapter;

    @Test
    void findByOrganizationAndIds_usesOneScopedBatchQueryAndMapsSummaries() {
        Set<UUID> ids = Set.of(FINANCE_SPACE_ID, ARCHIVES_SPACE_ID);
        when(spaces.findByOrgIdAndIdIn(ORG_ID, ids)).thenReturn(List.of(
                space(FINANCE_SPACE_ID, "finance", "Finance", SpaceStatus.ACTIVE),
                space(ARCHIVES_SPACE_ID, "archives", "Archives", SpaceStatus.SUSPENDED)));

        List<SpaceContextSummary> result = adapter.findByOrganizationAndIds(ORG_ID, ids);

        assertThat(result).extracting("id").containsExactly(FINANCE_SPACE_ID, ARCHIVES_SPACE_ID);
        assertThat(result).extracting("status").containsExactly("ACTIVE", "SUSPENDED");
        verify(spaces).findByOrgIdAndIdIn(ORG_ID, ids);
    }

    @Test
    void findByOrganizationAndIds_emptySet_skipsRepository() {
        assertThat(adapter.findByOrganizationAndIds(ORG_ID, Set.of())).isEmpty();

        verify(spaces, never()).findByOrgIdAndIdIn(org.mockito.Mockito.any(), org.mockito.Mockito.any());
    }

    private SpaceEntity space(UUID id, String code, String name, SpaceStatus status) {
        return SpaceEntity.builder()
                .id(id)
                .orgId(ORG_ID)
                .code(code)
                .name(name)
                .status(status)
                .build();
    }
}
