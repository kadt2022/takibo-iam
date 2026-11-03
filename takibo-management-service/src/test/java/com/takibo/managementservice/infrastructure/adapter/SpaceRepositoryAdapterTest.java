package com.takibo.managementservice.infrastructure.adapter;

import com.takibo.managementservice.domain.model.Space;
import com.takibo.managementservice.domain.model.SpaceStatus;
import com.takibo.managementservice.domain.vo.SpaceId;
import com.takibo.managementservice.infrastructure.entity.SpaceEntity;
import com.takibo.managementservice.infrastructure.jpa.mapper.SpaceJpaMapperImpl;
import com.takibo.managementservice.infrastructure.jpa.repository.JpaSpaceRepository;
import com.takibo.managementservice.infrastructure.jpa.repository.SpringDataSpaceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SpaceRepositoryAdapterTest {

    private static final UUID ORG_ID = UUID.fromString("aaaaaaaa-0000-0000-0000-000000000001");
    private static final UUID SPACE_ID = UUID.fromString("bbbbbbbb-0000-0000-0000-000000000002");

    private SpringDataSpaceRepository springData;
    private JpaSpaceRepository spaces;
    private SpaceRepositoryAdapter adapter;

    @BeforeEach
    void setUp() {
        springData = mock(SpringDataSpaceRepository.class);
        spaces = mock(JpaSpaceRepository.class);
        adapter = new SpaceRepositoryAdapter(springData, spaces, new SpaceJpaMapperImpl());
    }

    @Test
    void save_flushesTheMappedSpaceAndReturnsWhatTheDatabaseStored() {
        Space space = Space.createNew(SpaceId.of(SPACE_ID), ORG_ID, null, "ops", "Ops", null,
                Instant.parse("2030-01-01T00:00:00Z"));
        Instant storedAt = Instant.parse("2030-01-01T00:00:01Z");
        when(springData.saveAndFlush(any(SpaceEntity.class))).thenAnswer(invocation -> {
            SpaceEntity entity = invocation.getArgument(0);
            entity.setCreatedAt(storedAt);
            entity.setUpdatedAt(storedAt);
            entity.setVersion(1L);
            return entity;
        });

        Space saved = adapter.save(space);

        ArgumentCaptor<SpaceEntity> written = ArgumentCaptor.forClass(SpaceEntity.class);
        verify(springData).saveAndFlush(written.capture());
        assertThat(written.getValue().getId()).isEqualTo(SPACE_ID);
        assertThat(written.getValue().getOrgId()).isEqualTo(ORG_ID);
        assertThat(written.getValue().getCode()).isEqualTo("ops");
        assertThat(saved.getId()).isEqualTo(SpaceId.of(SPACE_ID));
        assertThat(saved.getCreatedAt()).isEqualTo(storedAt);
        assertThat(saved.getVersion()).isEqualTo(1L);
    }

    @Test
    void codeLookupsAreScopedToTheOrganization() {
        SpaceEntity entity = SpaceEntity.builder()
                .id(SPACE_ID).orgId(ORG_ID).code("ops").name("Ops").status(SpaceStatus.ACTIVE).build();
        when(spaces.existsByOrgIdAndCode(ORG_ID, "ops")).thenReturn(true);
        when(spaces.findByOrgIdAndCode(ORG_ID, "ops")).thenReturn(Optional.of(entity));

        assertThat(adapter.existsByOrgIdAndCode(ORG_ID, "ops")).isTrue();
        assertThat(adapter.existsByOrgIdAndCode(UUID.randomUUID(), "ops")).isFalse();
        assertThat(adapter.findByOrgIdAndCode(ORG_ID, "ops"))
                .get()
                .satisfies(space -> {
                    assertThat(space.getId()).isEqualTo(SpaceId.of(SPACE_ID));
                    assertThat(space.getOrgId()).isEqualTo(ORG_ID);
                });
        assertThat(adapter.findByOrgIdAndCode(UUID.randomUUID(), "ops")).isEmpty();
    }

    @Test
    void statusReadsAndWritesDelegateToTheStatusQueries() {
        Instant at = Instant.parse("2030-01-02T00:00:00Z");
        when(springData.findStatusById(SPACE_ID)).thenReturn(Optional.of(SpaceStatus.SUSPENDED));
        when(springData.findStatusUpdatedAtById(SPACE_ID)).thenReturn(Optional.of(at));
        when(springData.updateStatus(SPACE_ID, SpaceStatus.DISABLED, "closed", at)).thenReturn(1);

        assertThat(adapter.findStatusById(SPACE_ID)).contains(SpaceStatus.SUSPENDED);
        assertThat(adapter.findStatusUpdatedAtById(SPACE_ID)).contains(at);
        assertThat(adapter.updateStatus(SPACE_ID, SpaceStatus.DISABLED, "closed", at)).isEqualTo(1);
        assertThat(adapter.findStatusById(UUID.randomUUID())).isEmpty();
    }
}
