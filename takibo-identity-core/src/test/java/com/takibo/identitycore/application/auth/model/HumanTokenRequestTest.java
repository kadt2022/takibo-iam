package com.takibo.identitycore.application.auth.model;

import com.takibo.identitycore.domain.catalogrbac.AuthorityPlan;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class HumanTokenRequestTest {

    private static final UUID ORG_ID =
            UUID.fromString("aaaaaaaa-0000-0000-0000-000000000001");
    private static final UUID SPACE_ID =
            UUID.fromString("bbbbbbbb-0000-0000-0000-000000000002");
    private static final UUID ACCOUNT_ID =
            UUID.fromString("cccccccc-0000-0000-0000-000000000003");
    private static final UUID USER_ID =
            UUID.fromString("dddddddd-0000-0000-0000-000000000004");

    @Test
    void spaceRequest_preservesRealOrganizationRoleWithSpacePermissions() {
        HumanTokenRequest request = HumanTokenRequest.spaceScoped(
                ORG_ID,
                SPACE_ID,
                ACCOUNT_ID,
                USER_ID,
                List.of("R_ORG_ADMIN"),
                List.of("G_ORG_ADMINS"),
                List.of("P_SPACE_READ", "P_SPACE_USERS_MANAGE"));

        assertThat(request.authorityPlan()).isEqualTo(AuthorityPlan.SPACE);
        assertThat(request.source()).isEqualTo(HumanTokenSource.SPACE_SELECTION);
        assertThat(request.roles()).containsExactly("R_ORG_ADMIN");
        assertThat(request.roles()).doesNotContain("R_SPACE_ADMIN");
        assertThat(request.permissions()).allMatch(code -> code.startsWith("P_SPACE_"));
    }

    @Test
    void organizationRequest_carriesOrganizationBoundaryOnly() {
        HumanTokenRequest request = HumanTokenRequest.organizationScoped(
                ORG_ID,
                ACCOUNT_ID,
                List.of("R_ORG_ADMIN"),
                List.of("G_ORG_ADMINS"),
                List.of("P_ORG_READ"));

        assertThat(request.authorityPlan()).isEqualTo(AuthorityPlan.ORGANIZATION);
        assertThat(request.source()).isEqualTo(HumanTokenSource.ORGANIZATION_LOGIN);
        assertThat(request.spaceId()).isNull();
        assertThat(request.userId()).isNull();
    }

    @Test
    void request_rejectsPermissionFromAnotherPlan() {
        assertThatThrownBy(() -> HumanTokenRequest.spaceScoped(
                ORG_ID,
                SPACE_ID,
                ACCOUNT_ID,
                USER_ID,
                List.of("R_ORG_ADMIN"),
                List.of(),
                List.of("P_ORG_USERS_MANAGE")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("incompatible with SPACE token");

        assertThatThrownBy(() -> HumanTokenRequest.organizationScoped(
                ORG_ID,
                ACCOUNT_ID,
                List.of("R_ORG_ADMIN"),
                List.of(),
                List.of("P_SPACE_USERS_MANAGE")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("incompatible with ORGANIZATION token");
    }

    @Test
    void request_rejectsUnknownPermissionAndMismatchedSource() {
        assertThatThrownBy(() -> HumanTokenRequest.spaceScoped(
                ORG_ID,
                SPACE_ID,
                ACCOUNT_ID,
                USER_ID,
                List.of(),
                List.of(),
                List.of("P_SPACE_UNKNOWN")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unknown canonical permission");

        assertThatThrownBy(() -> new HumanTokenRequest(
                ORG_ID,
                SPACE_ID,
                ACCOUNT_ID,
                USER_ID,
                HumanTokenSource.ORGANIZATION_LOGIN,
                List.of(),
                List.of(),
                List.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("incompatible with SPACE scope");
    }
}
