package com.takibo.identitycore.application.rbac.service;

import com.takibo.identitycore.domain.exception.ReservedTenantRoleCodeException;
import com.takibo.identitycore.domain.repository.RoleRepository;
import com.takibo.identitycore.domain.vo.SpaceId;
import com.takibo.identitycore.integration.space.port.SpaceStatusCheckerCase;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class RoleApplicationServiceTest {

    private static final UUID SPACE_ID =
            UUID.fromString("bbbbbbbb-0000-0000-0000-000000000002");

    @Mock private RoleRepository roleRepository;
    @Mock private SpaceStatusCheckerCase spaceStatusCheckerCase;

    @InjectMocks
    private RoleApplicationService service;

    @Test
    void ensure_reservedCodeFailsBeforeExistingRoleLookup() {
        SpaceId spaceId = SpaceId.of(SPACE_ID);

        assertThatThrownBy(() ->
                service.ensure(spaceId, "R_ORG_CUSTOM", "Custom", null))
                .isInstanceOf(ReservedTenantRoleCodeException.class);

        verifyNoInteractions(roleRepository, spaceStatusCheckerCase);
    }

    @Test
    void ensureRole_reservedCodeFailsBeforeExistingRoleLookup() {
        assertThatThrownBy(() ->
                service.ensureRole(SPACE_ID, "PLATFORM_ADMIN", "Admin", null))
                .isInstanceOf(ReservedTenantRoleCodeException.class);

        verifyNoInteractions(roleRepository, spaceStatusCheckerCase);
    }
}
