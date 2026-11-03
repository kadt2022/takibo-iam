package com.takibo.identitycore.infrastructure.adapter;

import com.takibo.identitycore.domain.model.Group;
import com.takibo.identitycore.domain.rbac.model.GroupReference;
import com.takibo.identitycore.domain.vo.GroupId;
import com.takibo.identitycore.domain.vo.SpaceId;
import com.takibo.identitycore.infrastructure.entity.GroupEntity;
import com.takibo.identitycore.infrastructure.jpa.mapper.GroupJpaMapper;
import com.takibo.identitycore.infrastructure.jpa.repository.JpaGroupRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GroupRepositoryAdapterTest {

    private static final UUID SPACE_ID = UUID.fromString("bbbbbbbb-0000-0000-0000-000000000002");
    private static final UUID GROUP_ID = UUID.fromString("cccccccc-0000-0000-0000-000000000003");

    @Mock private JpaGroupRepository jpa;
    @Mock private GroupJpaMapper mapper;

    @InjectMocks
    private GroupRepositoryAdapter adapter;

    @Test
    void findReferencesBySpaceIdAndCodeIn_mapsEntitiesToDomainReferences() {
        GroupEntity entity = GroupEntity.builder()
                .id(GROUP_ID)
                .spaceId(SPACE_ID)
                .code("G_SPACE_ADMINS")
                .build();

        List<String> groupCodes = List.of("G_SPACE_ADMINS");
        when(jpa.findBySpaceIdAndCodeIn(SPACE_ID, groupCodes)).thenReturn(List.of(entity));

        List<GroupReference> result = adapter.findReferencesBySpaceIdAndCodeIn(SPACE_ID, groupCodes);

        assertThat(result).containsExactly(new GroupReference(GROUP_ID, "G_SPACE_ADMINS"));
        verify(jpa).findBySpaceIdAndCodeIn(SPACE_ID, groupCodes);
    }

    @Test
    void findById_delegatesToJpaAndMapsToDomain() {
        GroupId groupId = GroupId.of(GROUP_ID);
        GroupEntity entity = GroupEntity.builder().id(GROUP_ID).spaceId(SPACE_ID).code("G_A").build();
        Group domain = mock(Group.class);

        when(jpa.findById(GROUP_ID)).thenReturn(Optional.of(entity));
        when(mapper.toDomain(entity)).thenReturn(domain);

        Optional<Group> result = adapter.findById(groupId);

        assertThat(result).contains(domain);
        verify(jpa).findById(GROUP_ID);
    }

    @Test
    void save_persistsMappedEntity_andReturnsThePersistedState() {
        Group group = mock(Group.class);
        GroupEntity entity = GroupEntity.builder().id(GROUP_ID).spaceId(SPACE_ID).code("G_A").build();
        GroupEntity persisted = GroupEntity.builder().id(GROUP_ID).spaceId(SPACE_ID).code("G_A").version(0L).build();
        Group reloaded = mock(Group.class);
        when(mapper.toEntity(group)).thenReturn(entity);
        when(jpa.save(entity)).thenReturn(persisted);
        when(mapper.toDomain(persisted)).thenReturn(reloaded);

        assertThat(adapter.save(group)).isSameAs(reloaded);
    }

    @Test
    void lookupsAreScopedToTheSpace() {
        GroupEntity entity = GroupEntity.builder().id(GROUP_ID).spaceId(SPACE_ID).code("G_A").build();
        Group domain = mock(Group.class);
        UUID orgId = UUID.fromString("aaaaaaaa-0000-0000-0000-000000000001");
        when(jpa.existsBySpaceIdAndCode(SPACE_ID, "G_A")).thenReturn(true);
        when(jpa.findIdBySpaceIdAndCode(SPACE_ID, "G_A")).thenReturn(Optional.of(GROUP_ID));
        when(jpa.findBySpaceIdAndCode(SPACE_ID, "G_A")).thenReturn(Optional.of(entity));
        when(jpa.findByOrgIdAndSpaceIdOrderByCodeAsc(orgId, SPACE_ID)).thenReturn(List.of(entity));
        when(mapper.toDomain(entity)).thenReturn(domain);

        assertThat(adapter.existsBySpaceIdAndCode(SpaceId.of(SPACE_ID), "G_A")).isTrue();
        assertThat(adapter.findIdBySpaceIdAndCode(SpaceId.of(SPACE_ID), "G_A")).contains(GroupId.of(GROUP_ID));
        assertThat(adapter.findBySpaceIdAndCode(SpaceId.of(SPACE_ID), "G_A")).contains(domain);
        assertThat(adapter.findAllByOrgAndSpace(orgId, SPACE_ID)).containsExactly(domain);
    }
}
