package com.takibo.securitymanagement.infrastructure.security;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class AuthorityFactoryTest {

    private List<String> authorities(Map<String, Object> claims) {
        return AuthorityFactory.from(claims, null).stream()
                .map(a -> a.getAuthority())
                .collect(Collectors.toList());
    }

    @Test
    void scope_claim_as_json_array_yields_scope_authorities() {
        // Format réel des access tokens Spring Authorization Server.
        Map<String, Object> claims = Map.of("scope", List.of("api.write", "api.read"));

        assertThat(authorities(claims)).contains("SCOPE_api.write", "SCOPE_api.read");
    }

    @Test
    void scope_claim_as_space_delimited_string_yields_scope_authorities() {
        Map<String, Object> claims = Map.of("scope", "api.write api.read");

        assertThat(authorities(claims)).contains("SCOPE_api.write", "SCOPE_api.read");
    }

    @Test
    void roles_and_permissions_claims_produce_role_prefixed_and_raw_authorities() {
        Map<String, Object> claims = Map.of(
                "roles", List.of("SPACE_ADMIN"),
                "permissions", List.of("MANAGE_CLIENTS"));

        List<String> authorities = authorities(claims);
        assertThat(authorities).contains("ROLE_SPACE_ADMIN", "MANAGE_CLIENTS");
    }
}
