package com.takibo.managementservice.domain.service;

import com.takibo.managementservice.domain.exception.OrganizationDisabledException;
import com.takibo.managementservice.domain.exception.SpaceQuotaExceededException;
import com.takibo.managementservice.domain.model.OrganizationContext;
import com.takibo.managementservice.domain.model.SpaceStatus;
import com.takibo.managementservice.domain.vo.SpaceId;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SpaceCreationDomainServiceTest {

    private final SpaceCreationDomainService service = new SpaceCreationDomainService();

    @Test
    void createSpace_creates_space_with_exactly_the_received_values_when_organization_allows_it() {
        UUID organizationId = UUID.fromString("aaaaaaaa-0000-0000-0000-000000000001");
        UUID ownerId = UUID.fromString("bbbbbbbb-0000-0000-0000-000000000002");
        SpaceId spaceId = SpaceId.of(UUID.fromString("cccccccc-0000-0000-0000-000000000003"));
        Instant now = Instant.parse("2026-07-20T12:00:00Z");

        var space = service.createSpace(
                new OrganizationContext(organizationId, true, 9),
                ownerId,
                "finance",
                "Finance",
                "Finance workspace",
                spaceId,
                now);

        assertThat(space.getId()).isEqualTo(spaceId);
        assertThat(space.getOrgId()).isEqualTo(organizationId);
        assertThat(space.getOwnerAccountId()).isEqualTo(ownerId);
        assertThat(space.getCode()).isEqualTo("finance");
        assertThat(space.getName()).isEqualTo("Finance");
        assertThat(space.getDescription()).isEqualTo("Finance workspace");
        assertThat(space.getStatus()).isEqualTo(SpaceStatus.ACTIVE);
        assertThat(space.getCreatedAt()).isEqualTo(now);
        assertThat(space.getUpdatedAt()).isEqualTo(now);
        assertThat(space.getStatusUpdatedAt()).isEqualTo(now);
    }

    @Test
    void createSpace_rejects_a_disabled_organization_before_creating_space() {
        UUID organizationId = UUID.fromString("aaaaaaaa-0000-0000-0000-000000000001");

        assertThatThrownBy(() -> service.createSpace(
                new OrganizationContext(organizationId, false, 0),
                UUID.fromString("bbbbbbbb-0000-0000-0000-000000000002"),
                "finance",
                "Finance",
                "Finance workspace",
                SpaceId.of(UUID.fromString("cccccccc-0000-0000-0000-000000000003")),
                Instant.parse("2026-07-20T12:00:00Z")))
                .isInstanceOf(OrganizationDisabledException.class)
                .hasMessage("Organization is disabled: " + organizationId);
    }

    @Test
    void createSpace_rejects_an_organization_at_quota_with_current_usage() {
        UUID organizationId = UUID.fromString("aaaaaaaa-0000-0000-0000-000000000001");
        int currentSpaces = 12;

        assertThatThrownBy(() -> service.createSpace(
                new OrganizationContext(organizationId, true, currentSpaces),
                UUID.fromString("bbbbbbbb-0000-0000-0000-000000000002"),
                "finance",
                "Finance",
                "Finance workspace",
                SpaceId.of(UUID.fromString("cccccccc-0000-0000-0000-000000000003")),
                Instant.parse("2026-07-20T12:00:00Z")))
                .isInstanceOf(SpaceQuotaExceededException.class)
                .hasMessage("Space quota exceeded for orgId=" + organizationId
                        + " max=10 current=" + currentSpaces);
    }

    @Test
    void createSpace_preserves_organization_boundary_from_context() {
        UUID commandOrganizationId = UUID.fromString("aaaaaaaa-0000-0000-0000-000000000001");
        UUID contextOrganizationId = UUID.fromString("dddddddd-0000-0000-0000-000000000004");

        var space = service.createSpace(
                new OrganizationContext(contextOrganizationId, true, 0),
                UUID.fromString("bbbbbbbb-0000-0000-0000-000000000002"),
                "finance",
                "Finance",
                "Finance workspace",
                SpaceId.of(UUID.fromString("cccccccc-0000-0000-0000-000000000003")),
                Instant.parse("2026-07-20T12:00:00Z"));

        assertThat(space.getOrgId())
                .isEqualTo(contextOrganizationId)
                .isNotEqualTo(commandOrganizationId);
    }
}
