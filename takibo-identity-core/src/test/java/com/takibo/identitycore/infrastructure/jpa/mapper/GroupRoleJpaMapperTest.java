package com.takibo.identitycore.infrastructure.jpa.mapper;

import com.takibo.identitycore.domain.model.GroupRole;
import com.takibo.identitycore.domain.vo.GroupId;
import com.takibo.identitycore.domain.vo.RoleId;
import com.takibo.identitycore.domain.vo.SpaceId;
import com.takibo.identitycore.infrastructure.entity.GroupRoleEntity;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class GroupRoleJpaMapperTest {

    private static final UUID LINK_UUID = UUID.fromString("eeeeeeee-0000-0000-0000-000000000005");
    private static final UUID SPACE_UUID = UUID.fromString("bbbbbbbb-0000-0000-0000-000000000002");
    private static final UUID GROUP_UUID = UUID.fromString("cccccccc-0000-0000-0000-000000000003");
    private static final UUID ROLE_UUID = UUID.fromString("dddddddd-0000-0000-0000-000000000004");
    private static final UUID ACTOR_UUID = UUID.fromString("ffffffff-0000-0000-0000-000000000006");
    private static final Instant ASSIGNED = Instant.parse("2026-01-01T00:00:00Z");

    private final GroupRoleJpaMapper mapper = Mappers.getMapper(GroupRoleJpaMapper.class);

    @Test
    void toEntity_keepsTheLinkInsideItsSpace_withoutLoadingAssociations() {
        GroupRole link = GroupRole.builder()
                .id(LINK_UUID)
                .spaceId(SpaceId.of(SPACE_UUID))
                .groupId(GroupId.of(GROUP_UUID))
                .roleId(RoleId.of(ROLE_UUID))
                .assignedAt(ASSIGNED).assignedBy(ACTOR_UUID)
                .createdAt(ASSIGNED).updatedAt(ASSIGNED)
                .version(1L)
                .build();

        GroupRoleEntity entity = mapper.toEntity(link);

        assertThat(entity.getId()).isEqualTo(LINK_UUID);
        assertThat(entity.getSpaceId()).isEqualTo(SPACE_UUID);
        assertThat(entity.getGroupId()).isEqualTo(GROUP_UUID);
        assertThat(entity.getRoleId()).isEqualTo(ROLE_UUID);
        assertThat(entity.getAssignedAt()).isEqualTo(ASSIGNED);
        assertThat(entity.getAssignedBy()).isEqualTo(ACTOR_UUID);
        assertThat(entity.getVersion()).isEqualTo(1L);
        assertThat(entity.getGroup()).isNull();
        assertThat(entity.getRole()).isNull();
        assertThat(entity.getOrgId()).isNull();
        assertThat(entity.getCreatedAt()).isNull();
    }

    @Test
    void newLinkWithoutId_staysWithoutId_soPersistenceInserts() {
        GroupRole link = GroupRole.create(SpaceId.of(SPACE_UUID), GroupId.of(GROUP_UUID), RoleId.of(ROLE_UUID));

        assertThat(mapper.toEntity(link).getId()).isNull();
    }

    @Test
    void toDomain_wrapsEveryIdentifier() {
        GroupRoleEntity entity = GroupRoleEntity.builder()
                .id(LINK_UUID).spaceId(SPACE_UUID).groupId(GROUP_UUID).roleId(ROLE_UUID)
                .assignedAt(ASSIGNED).assignedBy(ACTOR_UUID)
                .createdAt(ASSIGNED).updatedAt(ASSIGNED).version(2L)
                .build();

        GroupRole link = mapper.toDomain(entity);

        assertThat(link.getId()).isEqualTo(LINK_UUID);
        assertThat(link.getSpaceId()).isEqualTo(SpaceId.of(SPACE_UUID));
        assertThat(link.getGroupId()).isEqualTo(GroupId.of(GROUP_UUID));
        assertThat(link.getRoleId()).isEqualTo(RoleId.of(ROLE_UUID));
        assertThat(link.getAssignedBy()).isEqualTo(ACTOR_UUID);
        assertThat(link.getVersion()).isEqualTo(2L);
    }

    @Test
    void nullsAreTolerated() {
        assertThat(mapper.toEntity(null)).isNull();
        assertThat(mapper.toDomain(null)).isNull();
        GroupRole link = mapper.toDomain(new GroupRoleEntity());
        assertThat(link.getSpaceId()).isNull();
        assertThat(link.getGroupId()).isNull();
        assertThat(link.getRoleId()).isNull();
    }
}
