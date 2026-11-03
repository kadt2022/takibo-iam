package com.takibo.managementservice.domain.model;

import com.takibo.managementservice.domain.vo.SpaceId;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

class SpaceTest {

    private static final UUID ORG_ID = UUID.fromString("aaaaaaaa-0000-0000-0000-000000000001");
    private static final UUID OWNER_ID = UUID.fromString("cccccccc-0000-0000-0000-000000000003");
    private static final Instant NOW = Instant.parse("2020-01-01T00:00:00Z");

    @Test
    void a_new_space_starts_active_inside_its_organization() {
        SpaceId id = SpaceId.newId();

        Space space = Space.createNew(id, ORG_ID, OWNER_ID, "main", "Main", "Primary", NOW);

        assertThat(space.getId()).isEqualTo(id);
        assertThat(space.getOrgId()).isEqualTo(ORG_ID);
        assertThat(space.getOwnerAccountId()).isEqualTo(OWNER_ID);
        assertThat(space.getCode()).isEqualTo("main");
        assertThat(space.getName()).isEqualTo("Main");
        assertThat(space.getDescription()).isEqualTo("Primary");
        assertThat(space.getStatus()).isEqualTo(SpaceStatus.ACTIVE);
        assertThat(space.getStatusReason()).isNull();
        assertThat(space.getStatusUpdatedAt()).isEqualTo(NOW);
        assertThat(space.getCreatedAt()).isEqualTo(NOW);
        assertThat(space.getUpdatedAt()).isEqualTo(NOW);
        assertThat(space.getVersion()).isZero();
    }

    @Test
    void the_short_factories_generate_an_identity_and_keep_the_owner_when_given() {
        Space withoutOwner = Space.createNew("ops", ORG_ID, "Ops", null);
        Space withOwner = Space.createNew("ops", ORG_ID, OWNER_ID, "Ops", null);

        assertThat(withoutOwner.getId()).isNotNull();
        assertThat(withoutOwner.getOwnerAccountId()).isNull();
        assertThat(withoutOwner.getStatus()).isEqualTo(SpaceStatus.ACTIVE);
        assertThat(withOwner.getOwnerAccountId()).isEqualTo(OWNER_ID);
        assertThat(withOwner.getId()).isNotEqualTo(withoutOwner.getId());
    }

    @Test
    void a_space_cannot_be_created_outside_an_organization_or_without_identity() {
        SpaceId id = SpaceId.newId();

        assertThatNullPointerException()
                .isThrownBy(() -> Space.createNew(id, null, OWNER_ID, "c", "n", null, NOW))
                .withMessage("orgId");
        assertThatNullPointerException()
                .isThrownBy(() -> Space.createNew(null, ORG_ID, OWNER_ID, "c", "n", null, NOW))
                .withMessage("id");
        assertThatNullPointerException()
                .isThrownBy(() -> Space.createNew(id, ORG_ID, OWNER_ID, null, "n", null, NOW))
                .withMessage("code");
        assertThatNullPointerException()
                .isThrownBy(() -> Space.createNew(id, ORG_ID, OWNER_ID, "c", null, null, NOW))
                .withMessage("name");
        assertThatNullPointerException()
                .isThrownBy(() -> Space.createNew(id, ORG_ID, OWNER_ID, "c", "n", null, null))
                .withMessage("now");
    }

    @Test
    void changing_the_status_keeps_identity_and_boundary_and_records_the_reason() {
        Space space = Space.createNew(SpaceId.newId(), ORG_ID, OWNER_ID, "main", "Main", null, NOW);

        Space suspended = space.updateStatus(SpaceStatus.SUSPENDED, "billing");

        assertThat(suspended).isNotSameAs(space).isEqualTo(space);
        assertThat(suspended.getOrgId()).isEqualTo(ORG_ID);
        assertThat(suspended.getCode()).isEqualTo("main");
        assertThat(suspended.getStatus()).isEqualTo(SpaceStatus.SUSPENDED);
        assertThat(suspended.getStatusReason()).isEqualTo("billing");
        assertThat(suspended.getStatusUpdatedAt()).isAfter(NOW);
        assertThat(suspended.getCreatedAt()).isEqualTo(NOW);
        assertThat(space.getStatus()).isEqualTo(SpaceStatus.ACTIVE);
    }

    @Test
    void updating_the_information_does_not_touch_the_status() {
        Space space = Space.createNew(SpaceId.newId(), ORG_ID, OWNER_ID, "main", "Main", null, NOW)
                .updateStatus(SpaceStatus.SUSPENDED, "audit");

        Space renamed = space.updateInfo("Renamed", "New description");

        assertThat(renamed.getName()).isEqualTo("Renamed");
        assertThat(renamed.getDescription()).isEqualTo("New description");
        assertThat(renamed.getStatus()).isEqualTo(SpaceStatus.SUSPENDED);
        assertThat(renamed.getStatusReason()).isEqualTo("audit");
        assertThat(renamed.getCode()).isEqualTo("main");
        assertThat(renamed.getUpdatedAt()).isAfter(NOW);
    }
}
