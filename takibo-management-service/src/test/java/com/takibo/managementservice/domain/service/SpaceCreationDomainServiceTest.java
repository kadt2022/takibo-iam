package com.takibo.managementservice.domain.service;

import com.takibo.managementservice.domain.model.OrganizationContext;
import com.takibo.managementservice.domain.model.SpaceStatus;
import com.takibo.managementservice.domain.vo.SpaceId;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class SpaceCreationDomainServiceTest {

    @Test
    void createSpace_builds_an_active_space_from_domain_values_only() {
        UUID organizationId = UUID.fromString("aaaaaaaa-0000-0000-0000-000000000001");
        UUID ownerId = UUID.fromString("bbbbbbbb-0000-0000-0000-000000000002");
        SpaceId spaceId = SpaceId.of(UUID.fromString("cccccccc-0000-0000-0000-000000000003"));
        Instant now = Instant.parse("2026-07-20T12:00:00Z");

        var space = new SpaceCreationDomainService().createSpace(
                new OrganizationContext(organizationId, true, 0),
                ownerId,
                "finance",
                "Finance",
                "Finance workspace",
                spaceId,
                now);

        assertThat(space.getId()).isEqualTo(spaceId);
        assertThat(space.getOrgId()).isEqualTo(organizationId);
        assertThat(space.getOwnerAccountId()).isEqualTo(ownerId);
        assertThat(space.getStatus()).isEqualTo(SpaceStatus.ACTIVE);
        assertThat(space.getCreatedAt()).isEqualTo(now);
    }
}
