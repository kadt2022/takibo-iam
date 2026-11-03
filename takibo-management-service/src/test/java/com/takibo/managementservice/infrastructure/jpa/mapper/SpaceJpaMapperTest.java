package com.takibo.managementservice.infrastructure.jpa.mapper;

import com.takibo.managementservice.domain.model.Space;
import com.takibo.managementservice.domain.model.SpaceStatus;
import com.takibo.managementservice.domain.vo.SpaceId;
import com.takibo.managementservice.infrastructure.entity.SpaceEntity;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class SpaceJpaMapperTest {

    private static final UUID SPACE_ID = UUID.fromString("bbbbbbbb-0000-0000-0000-000000000002");
    private static final UUID ORG_ID = UUID.fromString("aaaaaaaa-0000-0000-0000-000000000001");
    private static final UUID OWNER_ID = UUID.fromString("cccccccc-0000-0000-0000-000000000003");
    private static final Instant CREATED = Instant.parse("2030-01-01T00:00:00Z");
    private static final Instant UPDATED = Instant.parse("2030-02-01T00:00:00Z");
    private static final Instant STATUS_AT = Instant.parse("2030-03-01T00:00:00Z");

    private final SpaceJpaMapper mapper = new SpaceJpaMapperImpl();

    @Test
    void toEntity_transposesTheSpaceInsideItsOrganization() {
        Space space = Space.builder()
                .id(SpaceId.of(SPACE_ID))
                .orgId(ORG_ID)
                .ownerAccountId(OWNER_ID)
                .code("ops")
                .name("Operations")
                .description("Ops space")
                .status(SpaceStatus.SUSPENDED)
                .statusReason("billing")
                .statusUpdatedAt(STATUS_AT)
                .createdAt(CREATED)
                .updatedAt(UPDATED)
                .version(7L)
                .build();

        SpaceEntity entity = mapper.toEntity(space);

        assertThat(entity.getId()).isEqualTo(SPACE_ID);
        assertThat(entity.getOrgId()).isEqualTo(ORG_ID);
        assertThat(entity.getOwnerAccountId()).isEqualTo(OWNER_ID);
        assertThat(entity.getCode()).isEqualTo("ops");
        assertThat(entity.getName()).isEqualTo("Operations");
        assertThat(entity.getDescription()).isEqualTo("Ops space");
        assertThat(entity.getStatus()).isEqualTo(SpaceStatus.SUSPENDED);
        assertThat(entity.getStatusReason()).isEqualTo("billing");
        assertThat(entity.getStatusUpdatedAt()).isEqualTo(STATUS_AT);
    }

    @Test
    void toEntity_leavesAuditAndVersionColumnsToJpa() {
        Space space = Space.builder()
                .id(SpaceId.of(SPACE_ID)).orgId(ORG_ID).code("ops").name("Ops")
                .status(SpaceStatus.ACTIVE)
                .createdAt(CREATED).updatedAt(UPDATED).version(7L)
                .build();

        SpaceEntity entity = mapper.toEntity(space);

        assertThat(entity.getCreatedAt()).isNull();
        assertThat(entity.getUpdatedAt()).isNull();
        assertThat(entity.getVersion()).isZero();
        assertThat(entity.getOrganization()).isNull();
        assertThat(entity.getDomains()).isEmpty();
        assertThat(entity.getOAuth2Clients()).isEmpty();
    }

    @Test
    void toDomain_restoresEveryColumnIncludingAuditAndVersion() {
        SpaceEntity entity = SpaceEntity.builder()
                .id(SPACE_ID)
                .orgId(ORG_ID)
                .ownerAccountId(OWNER_ID)
                .code("ops")
                .name("Operations")
                .description("Ops space")
                .status(SpaceStatus.DISABLED)
                .statusReason("closed")
                .statusUpdatedAt(STATUS_AT)
                .createdAt(CREATED)
                .updatedAt(UPDATED)
                .version(4L)
                .build();

        Space space = mapper.toDomain(entity);

        assertThat(space.getId()).isEqualTo(SpaceId.of(SPACE_ID));
        assertThat(space.getOrgId()).isEqualTo(ORG_ID);
        assertThat(space.getOwnerAccountId()).isEqualTo(OWNER_ID);
        assertThat(space.getCode()).isEqualTo("ops");
        assertThat(space.getName()).isEqualTo("Operations");
        assertThat(space.getDescription()).isEqualTo("Ops space");
        assertThat(space.getStatus()).isEqualTo(SpaceStatus.DISABLED);
        assertThat(space.getStatusReason()).isEqualTo("closed");
        assertThat(space.getStatusUpdatedAt()).isEqualTo(STATUS_AT);
        assertThat(space.getCreatedAt()).isEqualTo(CREATED);
        assertThat(space.getUpdatedAt()).isEqualTo(UPDATED);
        assertThat(space.getVersion()).isEqualTo(4L);
    }

    @Test
    void unsavedSpacesWithoutIdentityAndNullInputsAreTolerated() {
        SpaceEntity withoutId = SpaceEntity.builder().orgId(ORG_ID).code("ops").name("Ops").build();

        assertThat(mapper.toDomain(withoutId).getId()).isNull();
        assertThat(mapper.toEntity(Space.builder().orgId(ORG_ID).code("ops").build()).getId()).isNull();
        assertThat(mapper.toEntity(null)).isNull();
        assertThat(mapper.toDomain(null)).isNull();
    }
}
