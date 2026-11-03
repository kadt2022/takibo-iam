package com.takibo.identitycore.infrastructure.jpa.mapper;

import com.takibo.identitycore.domain.model.Group;
import com.takibo.identitycore.domain.model.GroupNature;
import com.takibo.identitycore.domain.vo.GroupId;
import com.takibo.identitycore.domain.vo.SpaceId;
import com.takibo.identitycore.infrastructure.entity.GroupEntity;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mapstruct.factory.Mappers;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class GroupJpaMapperTest {

    private static final UUID GROUP_UUID = UUID.fromString("cccccccc-0000-0000-0000-000000000003");
    private static final UUID SPACE_UUID = UUID.fromString("bbbbbbbb-0000-0000-0000-000000000002");
    private static final Instant CREATED = Instant.parse("2026-01-01T00:00:00Z");
    private static final Instant UPDATED = Instant.parse("2026-02-01T00:00:00Z");

    private final GroupJpaMapper mapper = Mappers.getMapper(GroupJpaMapper.class);

    private static Group group(GroupNature nature) {
        return Group.builder()
                .id(GroupId.of(GROUP_UUID)).spaceId(SpaceId.of(SPACE_UUID))
                .nature(nature)
                .code("GRP_FINANCE").name("Finance").description("Finance team")
                .createdAt(CREATED).updatedAt(UPDATED).version(3L)
                .build();
    }

    @ParameterizedTest
    @EnumSource(GroupNature.class)
    void nature_survivesTheRoundTrip(GroupNature nature) {
        GroupEntity entity = mapper.toEntity(group(nature));

        assertThat(entity.getNature()).isEqualTo(nature);
        assertThat(mapper.toDomain(entity).getNature()).isEqualTo(nature);
    }

    @Test
    void toEntity_transposesTheSpaceScopedGroup_andLeavesTimestampsToPersistence() {
        GroupEntity entity = mapper.toEntity(group(GroupNature.BUSINESS));

        assertThat(entity.getId()).isEqualTo(GROUP_UUID);
        assertThat(entity.getSpaceId()).isEqualTo(SPACE_UUID);
        assertThat(entity.getCode()).isEqualTo("GRP_FINANCE");
        assertThat(entity.getName()).isEqualTo("Finance");
        assertThat(entity.getDescription()).isEqualTo("Finance team");
        assertThat(entity.getVersion()).isEqualTo(3L);
        assertThat(entity.getOrgId()).isNull();
        assertThat(entity.getCreatedAt()).isNull();
        assertThat(entity.getUpdatedAt()).isNull();
    }

    @Test
    void toDomain_wrapsIdentifiersAndKeepsTimestamps() {
        GroupEntity entity = GroupEntity.builder()
                .id(GROUP_UUID).spaceId(SPACE_UUID).nature(GroupNature.GOVERNANCE)
                .code("G_LOCAL").name("Local").createdAt(CREATED).updatedAt(UPDATED).version(1L)
                .build();

        Group group = mapper.toDomain(entity);

        assertThat(group.getId()).isEqualTo(GroupId.of(GROUP_UUID));
        assertThat(group.getSpaceId()).isEqualTo(SpaceId.of(SPACE_UUID));
        assertThat(group.getCode()).isEqualTo("G_LOCAL");
        assertThat(group.getCreatedAt()).isEqualTo(CREATED);
        assertThat(group.getUpdatedAt()).isEqualTo(UPDATED);
        assertThat(group.getVersion()).isEqualTo(1L);
    }

    @Test
    void nullsAreTolerated() {
        assertThat(mapper.toEntity(null)).isNull();
        assertThat(mapper.toDomain(null)).isNull();
        Group group = mapper.toDomain(GroupEntity.builder().code("X").build());
        assertThat(group.getId()).isNull();
        assertThat(group.getSpaceId()).isNull();
    }
}
