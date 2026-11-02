package com.takibo.managementservice.infrastructure.entity;

import com.takibo.managementservice.domain.model.OrganizationStatus;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class OrganizationEntityTest {

    @Test
    void prePersist_registersANewOrganizationAsActive() {
        OrganizationEntity organization = new OrganizationEntity();
        organization.setStatus(null);

        organization.prePersist();

        assertThat(organization.getId()).isNotNull();
        assertThat(organization.getStatus()).isEqualTo(OrganizationStatus.ACTIVE);
        assertThat(organization.getCreatedAt()).isNotNull();
        assertThat(organization.getUpdatedAt()).isEqualTo(organization.getCreatedAt());
    }

    @Test
    void prePersist_keepsAnExplicitStatusAndTimestamps() {
        UUID id = UUID.randomUUID();
        Instant at = Instant.parse("2030-01-01T00:00:00Z");
        OrganizationEntity organization = OrganizationEntity.builder()
                .id(id).status(OrganizationStatus.DISABLED).createdAt(at).updatedAt(at).build();

        organization.prePersist();

        assertThat(organization.getId()).isEqualTo(id);
        assertThat(organization.getStatus()).isEqualTo(OrganizationStatus.DISABLED);
        assertThat(organization.getCreatedAt()).isEqualTo(at);
        assertThat(organization.getUpdatedAt()).isEqualTo(at);
    }

    @Test
    void identityIsCarriedByThePrimaryKeyOnly() {
        UUID id = UUID.randomUUID();
        OrganizationEntity first = OrganizationEntity.builder().id(id).code("acme").build();

        assertThat(first).isEqualTo(OrganizationEntity.builder().id(id).code("other").build())
                .hasSameHashCodeAs(OrganizationEntity.builder().id(id).build());
        assertThat(first).isNotEqualTo(OrganizationEntity.builder().id(UUID.randomUUID()).build());
        assertThat(first).isNotEqualTo("acme");
        assertThat(first).isEqualTo(first);
    }
}
