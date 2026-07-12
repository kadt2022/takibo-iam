package com.takibo.managementservice.integration;

import com.takibo.identitycore.domain.exception.OrganizationNotActiveException;
import com.takibo.identitycore.domain.exception.OrganizationNotFoundException;
import com.takibo.identitycore.domain.exception.SpaceNotFoundException;
import com.takibo.identitycore.integration.space.port.ResolvedOrgKey;
import com.takibo.identitycore.integration.space.port.ResolvedSpaceKey;
import com.takibo.managementservice.domain.model.OrganizationStatus;
import com.takibo.managementservice.infrastructure.entity.OrganizationEntity;
import com.takibo.managementservice.infrastructure.entity.SpaceEntity;
import com.takibo.managementservice.infrastructure.jpa.repository.JpaOrganizationRepository;
import com.takibo.managementservice.infrastructure.jpa.repository.JpaSpaceRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SpaceKeyResolutionTmsAdapterTest {

    private static final UUID ORG_ID   = UUID.fromString("aaaaaaaa-0000-0000-0000-000000000001");
    private static final UUID SPACE_ID = UUID.fromString("bbbbbbbb-0000-0000-0000-000000000002");

    @Mock private JpaOrganizationRepository organizations;
    @Mock private JpaSpaceRepository spaces;

    @InjectMocks private SpaceKeyResolutionTmsAdapter adapter;

    @Test
    void given_raw_org_and_space_codes_when_resolve_then_returns_normalized_scoped_space_key() {
        // Inputs non normalisés : doivent être canonicalisés avant lookup.
        when(organizations.findByCode("takibo-iam"))
                .thenReturn(Optional.of(OrganizationEntity.builder().id(ORG_ID).code("takibo-iam").build()));
        when(spaces.findByOrgIdAndCode(ORG_ID, "finance"))
                .thenReturn(Optional.of(SpaceEntity.builder().id(SPACE_ID).orgId(ORG_ID).code("finance").build()));

        ResolvedSpaceKey key = adapter.resolve("  Takibo IAM ", "FINANCE");

        assertThat(key.orgId()).isEqualTo(ORG_ID);
        assertThat(key.spaceId()).isEqualTo(SPACE_ID);
        assertThat(key.orgCode()).isEqualTo("takibo-iam");
        assertThat(key.spaceCode()).isEqualTo("finance");

        // Le space est résolu scopé à l'org (jamais par code seul).
        verify(spaces).findByOrgIdAndCode(ORG_ID, "finance");
    }

    @Test
    void given_unknown_organization_code_when_resolve_then_throws_organization_not_found() {
        when(organizations.findByCode("ghost")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> adapter.resolve("Ghost", "finance"))
                .isInstanceOf(OrganizationNotFoundException.class)
                .hasMessageContaining("ghost");

        verify(spaces, never()).findByOrgIdAndCode(org.mockito.Mockito.any(), org.mockito.Mockito.any());
    }

    @Test
    void given_unknown_space_code_in_organization_when_resolve_then_throws_space_not_found() {
        when(organizations.findByCode("takibo"))
                .thenReturn(Optional.of(OrganizationEntity.builder().id(ORG_ID).code("takibo").build()));
        when(spaces.findByOrgIdAndCode(ORG_ID, "unknown")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> adapter.resolve("takibo", "Unknown"))
                .isInstanceOf(SpaceNotFoundException.class)
                .hasMessageContaining("unknown")
                .hasMessageContaining("takibo");
    }

    @Test
    void given_active_organization_when_resolve_active_organization_then_returns_normalized_org_key() {
        when(organizations.findByCode("takibo"))
                .thenReturn(Optional.of(OrganizationEntity.builder()
                        .id(ORG_ID)
                        .code("takibo")
                        .status(OrganizationStatus.ACTIVE)
                        .build()));

        ResolvedOrgKey key = adapter.resolveActiveOrganization(" Takibo ");

        assertThat(key.orgId()).isEqualTo(ORG_ID);
        assertThat(key.orgCode()).isEqualTo("takibo");
    }

    @Test
    void given_inactive_organization_when_resolve_active_organization_then_throws_not_active() {
        when(organizations.findByCode("takibo"))
                .thenReturn(Optional.of(OrganizationEntity.builder()
                        .id(ORG_ID)
                        .code("takibo")
                        .status(OrganizationStatus.SUSPENDED)
                        .build()));

        assertThatThrownBy(() -> adapter.resolveActiveOrganization("Takibo"))
                .isInstanceOf(OrganizationNotActiveException.class)
                .hasMessageContaining("takibo");
    }
}
