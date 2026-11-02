package com.takibo.identitycore.infrastructure.jpa.mapper;

import com.takibo.identitycore.domain.model.Identity;
import com.takibo.identitycore.domain.model.IdentityType;
import com.takibo.identitycore.domain.rbac.model.RoleAssignment;
import com.takibo.identitycore.domain.rbac.model.RoleSource;
import com.takibo.identitycore.infrastructure.entity.RoleAssignmentEntity;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mapstruct.factory.Mappers;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Une assignation de rôle porte sa frontière (org, space) et son sujet (type + id) :
 * la transposition ne doit ni les perdre ni en inventer.
 */
class RoleJpaAssignmentMapperTest {

    private static final UUID ASSIGNMENT_UUID = UUID.fromString("eeeeeeee-0000-0000-0000-000000000005");
    private static final UUID ORG_UUID = UUID.fromString("aaaaaaaa-0000-0000-0000-000000000001");
    private static final UUID SPACE_UUID = UUID.fromString("bbbbbbbb-0000-0000-0000-000000000002");
    private static final UUID ACCOUNT_UUID = UUID.fromString("cccccccc-0000-0000-0000-000000000003");
    private static final UUID ACTOR_UUID = UUID.fromString("ffffffff-0000-0000-0000-000000000006");
    private static final UUID BUSINESS_ROLE_UUID = UUID.fromString("dddddddd-0000-0000-0000-000000000004");
    private static final Instant CREATED = Instant.parse("2026-01-01T00:00:00Z");

    private final RoleJpaAssignmentMapper mapper = Mappers.getMapper(RoleJpaAssignmentMapper.class);

    private static RoleAssignment assignment(UUID spaceId, String createdBy) {
        return new RoleAssignment(ASSIGNMENT_UUID, ORG_UUID, spaceId,
                new Identity(IdentityType.ACCOUNT, ACCOUNT_UUID),
                "R_SPACE_ADMIN", RoleSource.TECHNICAL, null,
                CREATED, createdBy, CREATED, ACTOR_UUID.toString());
    }

    @Test
    void toEntity_flattensTheSubjectAndKeepsTheBoundary() {
        RoleAssignmentEntity entity = mapper.toEntity(assignment(SPACE_UUID, ACTOR_UUID.toString()));

        assertThat(entity.getId()).isEqualTo(ASSIGNMENT_UUID);
        assertThat(entity.getOrgId()).isEqualTo(ORG_UUID);
        assertThat(entity.getSpaceId()).isEqualTo(SPACE_UUID);
        assertThat(entity.getIdentityType()).isEqualTo("ACCOUNT");
        assertThat(entity.getIdentityId()).isEqualTo(ACCOUNT_UUID);
        assertThat(entity.getRoleCode()).isEqualTo("R_SPACE_ADMIN");
        assertThat(entity.getRoleSource()).isEqualTo(RoleSource.TECHNICAL);
        assertThat(entity.getBusinessRoleId()).isNull();
        assertThat(entity.getCreatedBy()).isEqualTo(ACTOR_UUID);
        assertThat(entity.getUpdatedBy()).isEqualTo(ACTOR_UUID);
        assertThat(entity.getCreatedAt()).isNull();
        assertThat(entity.getIdentity()).isNull();
    }

    @Test
    void toEntity_orgLevelAssignment_keepsTheNullSpace() {
        assertThat(mapper.toEntity(assignment(null, null)).getSpaceId()).isNull();
    }

    @ParameterizedTest
    @ValueSource(strings = {"SYSTEM", "system", "not-a-uuid", "   "})
    @NullAndEmptySource
    void toEntity_nonUuidActor_isNotPersistedAsActor(String createdBy) {
        assertThat(mapper.toEntity(assignment(SPACE_UUID, createdBy)).getCreatedBy()).isNull();
    }

    @Test
    void toEntity_withoutSubject_leavesSubjectColumnsEmpty() {
        RoleAssignment withoutIdentity = new RoleAssignment(null, ORG_UUID, null, null,
                null, RoleSource.BUSINESS, BUSINESS_ROLE_UUID, null, null, null, null);

        RoleAssignmentEntity entity = mapper.toEntity(withoutIdentity);

        assertThat(entity.getIdentityType()).isNull();
        assertThat(entity.getIdentityId()).isNull();
        assertThat(entity.getBusinessRoleId()).isEqualTo(BUSINESS_ROLE_UUID);
        assertThat(entity.getRoleSource()).isEqualTo(RoleSource.BUSINESS);
    }

    @Test
    void toDomain_rebuildsTheSubjectAndActors() {
        RoleAssignmentEntity entity = mapper.toEntity(assignment(SPACE_UUID, ACTOR_UUID.toString()));
        entity.setCreatedAt(CREATED);

        RoleAssignment domain = mapper.toDomain(entity);

        assertThat(domain.identity()).isEqualTo(new Identity(IdentityType.ACCOUNT, ACCOUNT_UUID));
        assertThat(domain.orgId()).isEqualTo(ORG_UUID);
        assertThat(domain.spaceId()).isEqualTo(SPACE_UUID);
        assertThat(domain.roleCode()).isEqualTo("R_SPACE_ADMIN");
        assertThat(domain.roleSource()).isEqualTo(RoleSource.TECHNICAL);
        assertThat(domain.createdAt()).isEqualTo(CREATED);
        assertThat(domain.createdBy()).isEqualTo(ACTOR_UUID.toString());
        assertThat(domain.updatedBy()).isEqualTo(ACTOR_UUID.toString());
    }

    @Test
    void toDomain_identityTypeIsParsedLeniently() {
        RoleAssignmentEntity entity = new RoleAssignmentEntity();
        entity.setIdentityType(" client ");
        entity.setIdentityId(ACCOUNT_UUID);

        assertThat(mapper.toDomain(entity).identity()).isEqualTo(new Identity(IdentityType.CLIENT, ACCOUNT_UUID));
    }

    @ParameterizedTest
    @ValueSource(strings = {"ROBOT", " "})
    void toDomain_unknownIdentityType_yieldsNoSubject(String identityType) {
        RoleAssignmentEntity entity = new RoleAssignmentEntity();
        entity.setIdentityType(identityType);
        entity.setIdentityId(ACCOUNT_UUID);

        assertThat(mapper.toDomain(entity).identity()).isNull();
    }

    @Test
    void toDomain_missingIdentityId_yieldsNoSubject() {
        RoleAssignmentEntity entity = new RoleAssignmentEntity();
        entity.setIdentityType("ACCOUNT");

        RoleAssignment domain = mapper.toDomain(entity);

        assertThat(domain.identity()).isNull();
        assertThat(domain.createdBy()).isNull();
    }

    @Test
    void nullsAreTolerated() {
        assertThat(mapper.toEntity(null)).isNull();
        assertThat(mapper.toDomain(null)).isNull();
        assertThat(mapper.toIdentity(null)).isNull();
    }
}
