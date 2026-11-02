package com.takibo.managementservice.infrastructure.entity;

import com.takibo.managementservice.domain.model.SpaceStatus;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class SpaceEntityTest {

    @Test
    void prePersist_registersANewSpaceAsActive() {
        SpaceEntity space = new SpaceEntity();
        space.setStatus(null);
        space.setStatusUpdatedAt(null);
        Instant before = Instant.now();

        space.prePersist();

        assertThat(space.getId()).isNotNull();
        assertThat(space.getStatus()).isEqualTo(SpaceStatus.ACTIVE);
        assertThat(space.getStatusUpdatedAt()).isBetween(before, Instant.now());
        assertThat(space.getCreatedAt()).isEqualTo(space.getStatusUpdatedAt());
        assertThat(space.getUpdatedAt()).isEqualTo(space.getCreatedAt());
    }

    @Test
    void prePersist_keepsAnExplicitStatusAndTimestamps() {
        UUID id = UUID.randomUUID();
        Instant at = Instant.parse("2020-01-01T00:00:00Z");
        SpaceEntity space = SpaceEntity.builder()
                .id(id)
                .status(SpaceStatus.SUSPENDED)
                .statusUpdatedAt(at)
                .createdAt(at)
                .updatedAt(at)
                .build();

        space.prePersist();

        assertThat(space.getId()).isEqualTo(id);
        assertThat(space.getStatus()).isEqualTo(SpaceStatus.SUSPENDED);
        assertThat(space.getStatusUpdatedAt()).isEqualTo(at);
        assertThat(space.getCreatedAt()).isEqualTo(at);
        assertThat(space.getUpdatedAt()).isEqualTo(at);
    }

    @Test
    void updateStatus_recordsTheReasonAndTheTransitionTime() {
        Instant at = Instant.parse("2020-01-01T00:00:00Z");
        SpaceEntity space = SpaceEntity.builder().status(SpaceStatus.ACTIVE).statusUpdatedAt(at).build();

        space.updateStatus(SpaceStatus.DISABLED, "closed by owner");

        assertThat(space.getStatus()).isEqualTo(SpaceStatus.DISABLED);
        assertThat(space.getStatusReason()).isEqualTo("closed by owner");
        assertThat(space.getStatusUpdatedAt()).isAfter(at);
    }

    @Test
    void domainsAreLinkedAndUnlinkedOnBothSides() {
        SpaceEntity space = SpaceEntity.builder().id(UUID.randomUUID()).build();
        SpaceDomainEntity kept = SpaceDomainEntity.builder().id(UUID.randomUUID()).domain("a.example").build();
        SpaceDomainEntity removed = SpaceDomainEntity.builder().id(UUID.randomUUID()).domain("b.example").build();

        space.addDomain(kept);
        space.addDomain(removed);
        space.removeDomain(removed);

        assertThat(space.getDomains()).containsExactly(kept);
        assertThat(kept.getSpace()).isSameAs(space);
        assertThat(removed.getSpace()).isNull();
    }

    @Test
    void identityIsCarriedByThePrimaryKeyOnly() {
        UUID id = UUID.randomUUID();
        SpaceEntity first = SpaceEntity.builder().id(id).code("a").build();
        SpaceEntity sameRow = SpaceEntity.builder().id(id).code("b").build();

        assertThat(first).isEqualTo(sameRow).hasSameHashCodeAs(sameRow);
        assertThat(first).isNotEqualTo(SpaceEntity.builder().id(UUID.randomUUID()).build());
        assertThat(first).isNotEqualTo(id);
        assertThat(first).isEqualTo(first);
    }
}
