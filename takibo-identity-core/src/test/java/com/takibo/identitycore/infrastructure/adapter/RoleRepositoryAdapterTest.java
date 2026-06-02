package com.takibo.identitycore.infrastructure.adapter;

import com.takibo.identitycore.domain.model.Role;
import com.takibo.identitycore.domain.model.RoleNature;
import com.takibo.identitycore.domain.vo.RoleId;
import com.takibo.identitycore.domain.vo.SpaceId;
import com.takibo.identitycore.infrastructure.entity.RoleEntity;
import com.takibo.identitycore.infrastructure.jpa.mapper.RoleJpaMapper;
import com.takibo.identitycore.infrastructure.jpa.repository.JpaRoleRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RoleRepositoryAdapterTest {

    private static final UUID ORG_ID   = UUID.fromString("aaaaaaaa-0000-0000-0000-000000000001");
    private static final UUID SPACE_ID = UUID.fromString("bbbbbbbb-0000-0000-0000-000000000002");
    private static final UUID ROLE_ID  = UUID.fromString("dddddddd-0000-0000-0000-000000000004");

    @Mock private JpaRoleRepository jpa;
    @Mock private RoleJpaMapper roleJpaMapper;

    @InjectMocks
    private RoleRepositoryAdapter adapter;

    @Test
    void findBusinessRolesByOrgAndSpaceAndCodes_queriesWithBusinessNatureAndMapsResult() {
        RoleEntity entity = roleEntity(RoleNature.BUSINESS, "MANAGER");
        Role domainRole = domainRole(RoleNature.BUSINESS, "MANAGER");

        when(jpa.findByOrgIdAndSpaceIdAndCodeInAndRoleNature(ORG_ID, SPACE_ID, List.of("MANAGER"), RoleNature.BUSINESS))
                .thenReturn(List.of(entity));
        when(roleJpaMapper.toDomain(entity)).thenReturn(domainRole);

        List<Role> result = adapter.findBusinessRolesByOrgAndSpaceAndCodes(ORG_ID, SPACE_ID, List.of("MANAGER"));

        assertThat(result).containsExactly(domainRole);
        verify(jpa).findByOrgIdAndSpaceIdAndCodeInAndRoleNature(ORG_ID, SPACE_ID, List.of("MANAGER"), RoleNature.BUSINESS);
    }

    @Test
    void findGovernanceRolesByOrgAndSpaceAndCodes_queriesWithGovernanceNatureAndMapsResult() {
        RoleEntity entity = roleEntity(RoleNature.GOVERNANCE, "R_SPACE_ADMIN");
        Role domainRole = domainRole(RoleNature.GOVERNANCE, "R_SPACE_ADMIN");

        when(jpa.findByOrgIdAndSpaceIdAndCodeInAndRoleNature(
                ORG_ID, SPACE_ID, List.of("R_SPACE_ADMIN"), RoleNature.GOVERNANCE))
                .thenReturn(List.of(entity));
        when(roleJpaMapper.toDomain(entity)).thenReturn(domainRole);

        List<Role> result = adapter.findGovernanceRolesByOrgAndSpaceAndCodes(
                ORG_ID, SPACE_ID, List.of("R_SPACE_ADMIN"));

        assertThat(result).containsExactly(domainRole);
        verify(jpa).findByOrgIdAndSpaceIdAndCodeInAndRoleNature(
                ORG_ID, SPACE_ID, List.of("R_SPACE_ADMIN"), RoleNature.GOVERNANCE);
    }

    @Test
    void findBusinessRoles_governanceRolePassedIn_returnsEmpty() {
        when(jpa.findByOrgIdAndSpaceIdAndCodeInAndRoleNature(
                ORG_ID, SPACE_ID, List.of("R_SPACE_ADMIN"), RoleNature.BUSINESS))
                .thenReturn(List.of());

        List<Role> result = adapter.findBusinessRolesByOrgAndSpaceAndCodes(
                ORG_ID, SPACE_ID, List.of("R_SPACE_ADMIN"));

        assertThat(result).isEmpty();
    }

    private RoleEntity roleEntity(RoleNature nature, String code) {
        return RoleEntity.builder()
                .id(ROLE_ID).orgId(ORG_ID).spaceId(SPACE_ID)
                .code(code).name(code).roleNature(nature)
                .build();
    }

    private Role domainRole(RoleNature nature, String code) {
        return Role.builder()
                .id(RoleId.of(ROLE_ID))
                .spaceId(SpaceId.of(SPACE_ID))
                .code(code).name(code).nature(nature)
                .createdAt(Instant.now()).updatedAt(Instant.now()).version(0L)
                .build();
    }
}
