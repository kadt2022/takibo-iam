package com.takibo.identitycore.domain.catalogrbac;

import com.takibo.identitycore.domain.exception.ReservedTenantRoleCodeException;
import com.takibo.identitycore.domain.model.Role;
import com.takibo.identitycore.domain.vo.SpaceId;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TenantRoleCodePolicyTest {

    private static final SpaceId SPACE_ID = SpaceId.of(
            UUID.fromString("bbbbbbbb-0000-0000-0000-000000000002"));

    @ParameterizedTest
    @ValueSource(strings = {
            "R_TAKIBO_CUSTOM", "R_ORG_CUSTOM", "R_SPACE_CUSTOM",
            "PLATFORM", "PLATFORM_ADMIN", "MY_PLATFORM_OPERATOR",
            "R_SELF"
    })
    void reservedNamespace_isRejectedCaseInsensitively(String code) {
        assertThatThrownBy(() -> TenantRoleCodePolicy.requireTenantCode(code.toLowerCase()))
                .isInstanceOf(ReservedTenantRoleCodeException.class)
                .hasMessageContaining(code.toLowerCase());
    }

    @Test
    void tenantNamespaces_areAccepted() {
        assertThatCode(() -> TenantRoleCodePolicy.requireTenantCode("GOV_LOCAL"))
                .doesNotThrowAnyException();
        assertThatCode(() -> TenantRoleCodePolicy.requireTenantCode("B_APPROVER"))
                .doesNotThrowAnyException();
    }

    @Test
    void bothTenantRoleFactories_enforceTheNamespaceBoundary() {
        assertThatThrownBy(() ->
                Role.createGovernanceRole(SPACE_ID, "PLATFORM_ADMIN", "Admin", null))
                .isInstanceOf(ReservedTenantRoleCodeException.class);
        assertThatThrownBy(() ->
                Role.createBusinessRole(SPACE_ID, "R_SPACE_CUSTOM", "Custom", null))
                .isInstanceOf(ReservedTenantRoleCodeException.class);
    }
}
