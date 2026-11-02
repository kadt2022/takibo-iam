package com.takibo.identitycore.infrastructure.jpa.mapper;

import com.takibo.identitycore.domain.model.Role;
import com.takibo.identitycore.domain.model.RoleNature;
import com.takibo.identitycore.domain.vo.RoleId;
import com.takibo.identitycore.domain.vo.SpaceId;
import com.takibo.identitycore.infrastructure.entity.RoleEntity;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mapstruct.factory.Mappers;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class RoleJpaMapperTest {

    private static final UUID ROLE_UUID = UUID.fromString("dddddddd-0000-0000-0000-000000000004");
    private static final UUID SPACE_UUID = UUID.fromString("bbbbbbbb-0000-0000-0000-000000000002");
    private static final UUID ORG_UUID = UUID.fromString("aaaaaaaa-0000-0000-0000-000000000001");
    private static final Instant CREATED = Instant.parse("2026-01-01T00:00:00Z");
    private static final Instant UPDATED = Instant.parse("2026-02-01T00:00:00Z");

    private final RoleJpaMapper mapper = Mappers.getMapper(RoleJpaMapper.class);

    private static Role role(RoleNature nature) {
        return Role.builder()
                .id(RoleId.of(ROLE_UUID)).spaceId(SpaceId.of(SPACE_UUID))
                .code("B_APPROVER").name("Approver").description("Approves")
                .nature(nature)
                .createdAt(CREATED).updatedAt(UPDATED).version(2L)
                .build();
    }

    @ParameterizedTest
    @EnumSource(RoleNature.class)
    void toEntity_keepsTheNatureThatSeparatesBusinessFromGovernance(RoleNature nature) {
        RoleEntity entity = mapper.toEntity(role(nature));

        assertThat(entity.getRoleNature()).isEqualTo(nature);
        assertThat(mapper.toDomain(entity).getNature()).isEqualTo(nature);
    }

    @Test
    void toEntity_transposesSpaceScopedIdentity_andLeavesTimestampsToPersistence() {
        RoleEntity entity = mapper.toEntity(role(RoleNature.BUSINESS));

        assertThat(entity.getId()).isEqualTo(ROLE_UUID);
        assertThat(entity.getSpaceId()).isEqualTo(SPACE_UUID);
        assertThat(entity.getCode()).isEqualTo("B_APPROVER");
        assertThat(entity.getName()).isEqualTo("Approver");
        assertThat(entity.getDescription()).isEqualTo("Approves");
        assertThat(entity.getVersion()).isEqualTo(2L);
        assertThat(entity.getOrgId()).isNull();
        assertThat(entity.getCreatedAt()).isNull();
        assertThat(entity.getUpdatedAt()).isNull();
    }

    @Test
    void toDomain_wrapsIdentifiersAndKeepsTimestamps() {
        RoleEntity entity = RoleEntity.builder()
                .id(ROLE_UUID).orgId(ORG_UUID).spaceId(SPACE_UUID)
                .code("GOV_LOCAL").name("Local").description(null)
                .roleNature(RoleNature.GOVERNANCE)
                .createdAt(CREATED).updatedAt(UPDATED).version(5L)
                .build();

        Role role = mapper.toDomain(entity);

        assertThat(role.getId()).isEqualTo(RoleId.of(ROLE_UUID));
        assertThat(role.getSpaceId()).isEqualTo(SpaceId.of(SPACE_UUID));
        assertThat(role.getCode()).isEqualTo("GOV_LOCAL");
        assertThat(role.getNature()).isEqualTo(RoleNature.GOVERNANCE);
        assertThat(role.getCreatedAt()).isEqualTo(CREATED);
        assertThat(role.getUpdatedAt()).isEqualTo(UPDATED);
        assertThat(role.getVersion()).isEqualTo(5L);
    }

    @Test
    void nullsAreTolerated() {
        assertThat(mapper.toEntity(null)).isNull();
        assertThat(mapper.toDomain(null)).isNull();
        Role role = mapper.toDomain(RoleEntity.builder().code("X").build());
        assertThat(role.getId()).isNull();
        assertThat(role.getSpaceId()).isNull();
    }
}
