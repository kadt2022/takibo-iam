package com.takibo.securitymanagement.infrastructure.security;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class TokenRoleFilterTest {

    private static final String ORG = "aaaaaaaa-0000-0000-0000-000000000001";
    private static final String SPACE = "bbbbbbbb-0000-0000-0000-000000000002";

    @Test
    void a_space_token_keeps_its_catalog_and_tenant_roles() {
        Map<String, Object> claims = spaceToken(List.of("R_SPACE_ADMIN", "R_ORG_ADMIN", "GOV_LOCAL", "finance-manager"));

        assertThat(TokenRoleFilter.sanitize(claims)).containsEntry("roles", List.of("R_SPACE_ADMIN", "R_ORG_ADMIN", "GOV_LOCAL", "finance-manager"));
    }

    @Test
    void an_organization_token_keeps_only_organization_catalog_roles() {
        Map<String, Object> claims = orgToken(List.of("R_ORG_OWNER", "R_ORG_ADMIN", "R_SPACE_ADMIN", "GOV_LOCAL"));

        assertThat(TokenRoleFilter.sanitize(claims)).containsEntry("roles", List.of("R_ORG_OWNER", "R_ORG_ADMIN"));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "R_TAKIBO_PLATFORM_ADMIN", "R_TAKIBO_PLATFORM_AUDITOR",
            "ROLE_R_TAKIBO_PLATFORM_ADMIN", "ROLE_R_ORG_ADMIN", "ROLE_R_ORG_OWNER", "ROLE_R_SPACE_ADMIN",
            "ROLE_GOV_LOCAL", "role_r_org_admin", "r_org_admin", "R_Org_Admin", " R_ORG_ADMIN", "R_ORG_ADMIN ",
            "R_SELF", "R_ORG_UNKNOWN", "R_SPACE_UNKNOWN", "R_TAKIBO_ANYTHING", "PLATFORM_ADMIN", "super-platform-role",
            "", "   "
    })
    void forged_privileged_or_malformed_roles_are_dropped_from_a_space_token(String role) {
        Map<String, Object> sanitized = TokenRoleFilter.sanitize(spaceToken(List.of("R_SPACE_VIEWER", role)));

        assertThat(sanitized).containsEntry("roles", List.of("R_SPACE_VIEWER"));
    }

    @Test
    void a_platform_role_is_never_admitted_in_a_tenant_token() {
        assertThat(TokenRoleFilter.sanitize(orgToken(List.of("R_TAKIBO_PLATFORM_ADMIN")))).doesNotContainKey("roles");
        assertThat(TokenRoleFilter.sanitize(spaceToken(List.of("R_TAKIBO_PLATFORM_ADMIN")))).doesNotContainKey("roles");
    }

    @Test
    void a_platform_token_keeps_only_platform_catalog_roles() {
        Map<String, Object> claims = platformToken(List.of(
                "R_TAKIBO_PLATFORM_ADMIN", "R_TAKIBO_PLATFORM_AUDITOR", "R_ORG_ADMIN", "R_SPACE_ADMIN",
                "GOV_LOCAL", "ROLE_R_TAKIBO_PLATFORM_ADMIN", "PLATFORM_ADMIN"));

        assertThat(TokenRoleFilter.sanitize(claims)).containsEntry("roles", List.of("R_TAKIBO_PLATFORM_ADMIN", "R_TAKIBO_PLATFORM_AUDITOR"));
    }

    @Test
    void a_platform_level_carrying_tenant_identifiers_admits_no_role() {
        Map<String, Object> withOrg = new HashMap<>(platformToken(List.of("R_TAKIBO_PLATFORM_ADMIN")));
        withOrg.put("orgId", ORG);
        Map<String, Object> withSpace = new HashMap<>(platformToken(List.of("R_TAKIBO_PLATFORM_ADMIN")));
        withSpace.put("spaceId", SPACE);

        assertThat(TokenRoleFilter.sanitize(withOrg)).doesNotContainKey("roles");
        assertThat(TokenRoleFilter.sanitize(withSpace)).doesNotContainKey("roles");
    }

    @Test
    void a_token_without_a_consistent_signed_level_admits_no_role() {
        Map<String, Object> noLevel = new HashMap<>(spaceToken(List.of("R_SPACE_ADMIN", "GOV_LOCAL")));
        noLevel.remove("scopeLevel");
        Map<String, Object> platform = new HashMap<>(spaceToken(List.of("R_ORG_ADMIN")));
        platform.put("scopeLevel", "PLATFORM");
        Map<String, Object> unknownLevel = new HashMap<>(spaceToken(List.of("R_SPACE_ADMIN")));
        unknownLevel.put("scopeLevel", "space");
        Map<String, Object> spaceWithoutSpaceId = new HashMap<>(spaceToken(List.of("R_SPACE_ADMIN")));
        spaceWithoutSpaceId.remove("spaceId");
        Map<String, Object> orgWithSpaceId = new HashMap<>(orgToken(List.of("R_ORG_ADMIN")));
        orgWithSpaceId.put("spaceId", SPACE);

        for (Map<String, Object> claims : List.of(noLevel, platform, unknownLevel, spaceWithoutSpaceId, orgWithSpaceId)) {
            assertThat(TokenRoleFilter.sanitize(claims)).as("claims %s", claims).doesNotContainKey("roles");
        }
    }

    @Test
    void a_single_string_roles_claim_is_validated_like_a_list() {
        Map<String, Object> claims = new HashMap<>(spaceToken(List.of()));
        claims.put("roles", "ROLE_R_TAKIBO_PLATFORM_ADMIN");

        assertThat(TokenRoleFilter.sanitize(claims)).doesNotContainKey("roles");
    }

    @Test
    void other_claims_are_left_untouched() {
        Map<String, Object> claims = new HashMap<>(spaceToken(List.of("ROLE_R_ORG_ADMIN")));
        claims.put("permissions", List.of("P_SPACE_USERS_READ"));
        claims.put("sub", "account-1");

        Map<String, Object> sanitized = TokenRoleFilter.sanitize(claims);

        assertThat(sanitized).containsEntry("permissions", List.of("P_SPACE_USERS_READ"))
                .containsEntry("sub", "account-1")
                .containsEntry("orgId", ORG)
                .containsEntry("spaceId", SPACE)
                .doesNotContainKey("roles");
    }

    @Test
    void claims_without_roles_are_returned_as_is() {
        Map<String, Object> claims = Map.of("sub", "client-app", "scope", "api.read");

        assertThat(TokenRoleFilter.sanitize(claims)).isSameAs(claims);
    }

    private static Map<String, Object> spaceToken(List<String> roles) {
        return Map.of("scopeLevel", "SPACE", "orgId", ORG, "spaceId", SPACE, "roles", roles);
    }

    private static Map<String, Object> platformToken(List<String> roles) {
        return Map.of("scopeLevel", "PLATFORM", "roles", roles);
    }

    private static Map<String, Object> orgToken(List<String> roles) {
        return Map.of("scopeLevel", "ORGANIZATION", "orgId", ORG, "roles", roles);
    }
}
