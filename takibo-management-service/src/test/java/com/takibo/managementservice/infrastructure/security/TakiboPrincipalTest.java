package com.takibo.managementservice.infrastructure.security;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class TakiboPrincipalTest {

    private static final UUID USER_ID = UUID.fromString("aaaaaaaa-0000-0000-0000-000000000001");
    private static final UUID ACCOUNT_ID = UUID.fromString("bbbbbbbb-0000-0000-0000-000000000002");

    @Test
    void fromClaims_maps_identifiers_and_collections_functionally() {
        TakiboPrincipal principal = TakiboPrincipal.fromClaims(Map.of(
                "sub", "subject",
                "preferred_username", "founder",
                "userId", USER_ID,
                "accountId", ACCOUNT_ID.toString(),
                "orgId", "invalid",
                "roles", List.of("R_ORG_OWNER", 42),
                "permissions", "space:read"));

        assertThat(principal.subject()).isEqualTo("subject");
        assertThat(principal.username()).isEqualTo("founder");
        assertThat(principal.userId()).isEqualTo(USER_ID);
        assertThat(principal.accountId()).isEqualTo(ACCOUNT_ID);
        assertThat(principal.orgId()).isNull();
        assertThat(principal.spaceId()).isNull();
        assertThat(principal.roles()).containsExactly("R_ORG_OWNER", "42");
        assertThat(principal.permissions()).containsExactly("space:read");
    }

    @Test
    void fromClaims_supplies_safe_defaults_for_missing_optional_claims() {
        TakiboPrincipal principal = TakiboPrincipal.fromClaims(Map.of());

        assertThat(principal.subject()).isEqualTo("anonymous");
        assertThat(principal.username()).isEqualTo("anonymous");
        assertThat(principal.roles()).isEmpty();
        assertThat(principal.permissions()).isEmpty();
    }
}
