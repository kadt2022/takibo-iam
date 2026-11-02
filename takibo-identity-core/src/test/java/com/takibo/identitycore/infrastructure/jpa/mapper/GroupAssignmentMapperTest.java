package com.takibo.identitycore.infrastructure.jpa.mapper;

import com.takibo.identitycore.domain.model.Identity;
import com.takibo.identitycore.domain.model.IdentityType;
import com.takibo.identitycore.domain.rbac.model.GroupAssignment;
import com.takibo.identitycore.domain.rbac.model.GroupSource;
import com.takibo.identitycore.infrastructure.entity.GroupAssignmentEntity;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mapstruct.factory.Mappers;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GroupAssignmentMapperTest {

    private static final UUID MEMBERSHIP_UUID = UUID.fromString("eeeeeeee-0000-0000-0000-000000000005");
    private static final UUID ORG_UUID = UUID.fromString("aaaaaaaa-0000-0000-0000-000000000001");
    private static final UUID SPACE_UUID = UUID.fromString("bbbbbbbb-0000-0000-0000-000000000002");
    private static final UUID ACCOUNT_UUID = UUID.fromString("cccccccc-0000-0000-0000-000000000003");
    private static final UUID ACTOR_UUID = UUID.fromString("ffffffff-0000-0000-0000-000000000006");
    private static final Instant CREATED = Instant.parse("2026-01-01T00:00:00Z");

    private final GroupAssignmentMapper mapper = Mappers.getMapper(GroupAssignmentMapper.class);

    private static GroupAssignment membership(GroupSource source, String createdBy) {
        return new GroupAssignment(MEMBERSHIP_UUID, ORG_UUID, SPACE_UUID,
                ACCOUNT_UUID, new Identity(IdentityType.ACCOUNT, ACCOUNT_UUID), IdentityType.ACCOUNT,
                "G_SPACE_ADMINS", source, null,
                CREATED, createdBy, CREATED, null);
    }

    @Test
    void toEntity_keepsBoundarySubjectAndCode() {
        GroupAssignmentEntity entity = mapper.toEntity(membership(GroupSource.TECHNICAL, ACTOR_UUID.toString()));

        assertThat(entity.getId()).isEqualTo(MEMBERSHIP_UUID);
        assertThat(entity.getOrgId()).isEqualTo(ORG_UUID);
        assertThat(entity.getSpaceId()).isEqualTo(SPACE_UUID);
        assertThat(entity.getIdentityType()).isEqualTo("ACCOUNT");
        assertThat(entity.getIdentityId()).isEqualTo(ACCOUNT_UUID);
        assertThat(entity.getGroupCode()).isEqualTo("G_SPACE_ADMINS");
        assertThat(entity.getGroupSource()).isEqualTo("TECHNICAL");
        assertThat(entity.getCreatedBy()).isEqualTo(ACTOR_UUID);
        assertThat(entity.getUpdatedBy()).isNull();
        assertThat(entity.getCreatedAt()).isNull();
        assertThat(entity.getIdentity()).isNull();
    }

    @ParameterizedTest
    @EnumSource(GroupSource.class)
    void groupSource_survivesTheRoundTrip(GroupSource source) {
        GroupAssignmentEntity entity = mapper.toEntity(membership(source, null));

        assertThat(entity.getGroupSource()).isEqualTo(source.name());
        assertThat(mapper.toDomain(entity).groupSource()).isEqualTo(source);
    }

    @Test
    void toDomain_unknownGroupSource_failsLoudly_insteadOfGuessingASource() {
        GroupAssignmentEntity entity = new GroupAssignmentEntity();
        entity.setGroupSource("ROOT");

        assertThatThrownBy(() -> mapper.toDomain(entity)).isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"SYSTEM", "not-a-uuid", "  "})
    @NullAndEmptySource
    void toEntity_nonUuidActor_isNotPersistedAsActor(String createdBy) {
        assertThat(mapper.toEntity(membership(GroupSource.TECHNICAL, createdBy)).getCreatedBy()).isNull();
    }

    @Test
    void toDomain_restoresBoundaryCodeAndActors() {
        GroupAssignmentEntity entity = mapper.toEntity(membership(GroupSource.GOVERNANCE, ACTOR_UUID.toString()));
        entity.setCreatedAt(CREATED);

        GroupAssignment domain = mapper.toDomain(entity);

        assertThat(domain.id()).isEqualTo(MEMBERSHIP_UUID);
        assertThat(domain.orgId()).isEqualTo(ORG_UUID);
        assertThat(domain.spaceId()).isEqualTo(SPACE_UUID);
        assertThat(domain.identityId()).isEqualTo(ACCOUNT_UUID);
        assertThat(domain.groupCode()).isEqualTo("G_SPACE_ADMINS");
        assertThat(domain.groupSource()).isEqualTo(GroupSource.GOVERNANCE);
        assertThat(domain.createdAt()).isEqualTo(CREATED);
        assertThat(domain.createdBy()).isEqualTo(ACTOR_UUID.toString());
        assertThat(domain.updatedBy()).isNull();
    }

    @Test
    void nullsAreTolerated() {
        assertThat(mapper.toEntity(null)).isNull();
        assertThat(mapper.toDomain(null)).isNull();
    }
}
