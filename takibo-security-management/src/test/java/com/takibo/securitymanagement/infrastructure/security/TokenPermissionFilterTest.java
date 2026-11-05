package com.takibo.securitymanagement.infrastructure.security;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class TokenPermissionFilterTest {

    private static final String ORG = "aaaaaaaa-0000-0000-0000-000000000001";
    private static final String SPACE = "bbbbbbbb-0000-0000-0000-000000000002";

    @Test
    void a_space_token_keeps_its_space_permissions() {
        Map<String, Object> claims = spaceToken(List.of("P_SPACE_USERS_READ", "P_SPACE_CLIENTS_MANAGE"));

        assertThat(TokenPermissionFilter.sanitize(claims))
                .containsEntry("permissions", List.of("P_SPACE_USERS_READ", "P_SPACE_CLIENTS_MANAGE"));
    }

    @Test
    void an_organization_token_keeps_its_organization_permissions() {
        Map<String, Object> claims = orgToken(List.of("P_ORG_USERS_READ", "P_ORG_SPACES_MANAGE"));

        assertThat(TokenPermissionFilter.sanitize(claims))
                .containsEntry("permissions", List.of("P_ORG_USERS_READ", "P_ORG_SPACES_MANAGE"));
    }

    @Test
    void a_platform_token_keeps_only_platform_permissions() {
        Map<String, Object> claims = Map.of("scopeLevel", "PLATFORM",
                "permissions", List.of("P_PLATFORM_ORGS_READ", "P_ORG_USERS_READ", "P_SPACE_USERS_READ"));

        assertThat(TokenPermissionFilter.sanitize(claims))
                .containsEntry("permissions", List.of("P_PLATFORM_ORGS_READ"));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            // plan incompatible : la projection ORG -> SPACE a déjà eu lieu à l'émission
            "P_ORG_USERS_READ", "P_ORG_SPACES_MANAGE", "P_PLATFORM_ORGS_READ",
            // inconnues ou anciennes
            "P_SPACE_UNKNOWN", "MANAGE_CLIENTS", "USER_READ", "SECRET_READ",
            // usurpations et formes mal écrites
            "ROLE_R_TAKIBO_PLATFORM_ADMIN", "ROLE_P_SPACE_USERS_READ", "SCOPE_api.read",
            "p_space_users_read", " P_SPACE_USERS_READ", "P_SPACE_USERS_READ ", "", "   "
    })
    void anything_but_a_catalog_permission_of_the_token_plan_is_dropped(String permission) {
        Map<String, Object> claims = spaceToken(List.of("P_SPACE_USERS_READ", permission));

        assertThat(TokenPermissionFilter.sanitize(claims)).containsEntry("permissions", List.of("P_SPACE_USERS_READ"));
    }

    @Test
    void a_space_permission_is_dropped_from_an_organization_token() {
        assertThat(TokenPermissionFilter.sanitize(orgToken(List.of("P_SPACE_USERS_READ"))))
                .doesNotContainKey("permissions");
    }

    @Test
    void a_token_without_a_consistent_signed_level_admits_no_permission() {
        Map<String, Object> noLevel = new HashMap<>(spaceToken(List.of("P_SPACE_USERS_READ")));
        noLevel.remove("scopeLevel");
        Map<String, Object> spaceWithoutSpaceId = new HashMap<>(spaceToken(List.of("P_SPACE_USERS_READ")));
        spaceWithoutSpaceId.remove("spaceId");
        Map<String, Object> platformWithOrg = Map.of("scopeLevel", "PLATFORM", "orgId", ORG,
                "permissions", List.of("P_PLATFORM_ORGS_READ"));

        for (Map<String, Object> claims : List.of(noLevel, spaceWithoutSpaceId, platformWithOrg)) {
            assertThat(TokenPermissionFilter.sanitize(claims)).as("claims %s", claims).doesNotContainKey("permissions");
        }
    }

    @Test
    void the_out_of_contract_authorities_claim_is_always_removed() {
        Map<String, Object> claims = new HashMap<>(spaceToken(List.of("P_SPACE_USERS_READ")));
        claims.put("authorities", List.of("ROLE_R_TAKIBO_PLATFORM_ADMIN", "P_SPACE_USERS_READ"));

        assertThat(TokenPermissionFilter.sanitize(claims))
                .doesNotContainKey("authorities")
                .containsEntry("permissions", List.of("P_SPACE_USERS_READ"));
    }

    @Test
    void oauth2_scopes_and_other_claims_are_left_untouched() {
        Map<String, Object> claims = new HashMap<>(spaceToken(List.of("USER_READ")));
        claims.put("scope", "api.read api.write");
        claims.put("roles", List.of("R_SPACE_ADMIN"));
        claims.put("sub", "account-1");

        assertThat(TokenPermissionFilter.sanitize(claims))
                .containsEntry("scope", "api.read api.write")
                .containsEntry("roles", List.of("R_SPACE_ADMIN"))
                .containsEntry("sub", "account-1")
                .doesNotContainKey("permissions");
    }

    @Test
    void a_client_token_without_permissions_is_returned_as_is() {
        Map<String, Object> claims = Map.of("sub", "billing-batch", "scope", "api.read", "scopeLevel", "SPACE");

        assertThat(TokenPermissionFilter.sanitize(claims)).isSameAs(claims);
    }

    private static Map<String, Object> spaceToken(List<String> permissions) {
        return Map.of("scopeLevel", "SPACE", "orgId", ORG, "spaceId", SPACE, "permissions", permissions);
    }

    private static Map<String, Object> orgToken(List<String> permissions) {
        return Map.of("scopeLevel", "ORGANIZATION", "orgId", ORG, "permissions", permissions);
    }
}
