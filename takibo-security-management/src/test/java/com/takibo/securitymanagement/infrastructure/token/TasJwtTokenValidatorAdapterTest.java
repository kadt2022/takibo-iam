package com.takibo.securitymanagement.infrastructure.token;

import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TasJwtTokenValidatorAdapterTest {

    private static final String ORG = "aaaaaaaa-0000-0000-0000-000000000001";
    private static final String SPACE = "bbbbbbbb-0000-0000-0000-000000000002";
    private static final String ACCOUNT = "cccccccc-0000-0000-0000-000000000003";
    private static final String USER = "dddddddd-0000-0000-0000-000000000004";

    private Map<String, Object> validate(Jwt jwt) {
        JwtDecoder decoder = mock(JwtDecoder.class);
        when(decoder.decode("raw")).thenReturn(jwt);
        return new TasJwtTokenValidatorAdapter(decoder).validate("raw");
    }

    @Test
    void mapsHumanTokenClaims() {
        Jwt jwt = Jwt.withTokenValue("raw")
                .header("alg", "RS256")
                .issuer("http://localhost:8081")
                .subject(ACCOUNT)
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(300))
                .claim("org_id", ORG)
                .claim("space_id", SPACE)
                .claim("account_id", ACCOUNT)
                .claim("user_id", USER)
                .claim("subject_type", "HUMAN")
                .claim("auth_method", "PASSWORD")
                .claim("takibo_scope_level", "SPACE")
                .claim("roles", List.of("R_ORG_OWNER", "R_SPACE_ADMIN"))
                .build();

        Map<String, Object> claims = validate(jwt);

        assertThat(claims.get("orgId")).isEqualTo(ORG);
        assertThat(claims.get("spaceId")).isEqualTo(SPACE);
        assertThat(claims.get("accountId")).isEqualTo(ACCOUNT);
        assertThat(claims.get("userId")).isEqualTo(USER);
        assertThat(claims.get("subjectType")).isEqualTo("HUMAN");
        assertThat(claims.get("authMethod")).isEqualTo("PASSWORD");
        assertThat(claims.get("scopeLevel")).isEqualTo("SPACE");
        assertThat(claims.get("roles")).isEqualTo(List.of("R_ORG_OWNER", "R_SPACE_ADMIN"));
    }

    @Test
    void platformToken_carriesNoTenantOrHumanIdentity() {
        Jwt jwt = Jwt.withTokenValue("raw")
                .header("alg", "RS256")
                .issuer("http://localhost:8081")
                .subject("postman-client")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(300))
                .claim("subject_type", "CLIENT_APP")
                .claim("auth_method", "OAUTH2_CLIENT_CREDENTIALS")
                .claim("takibo_scope_level", "PLATFORM")
                .claim("scope", List.of("api.read", "api.write"))
                .build();

        Map<String, Object> claims = validate(jwt);

        assertThat(claims).doesNotContainKeys("orgId", "spaceId", "accountId", "userId", "roles");
        assertThat(claims.get("subjectType")).isEqualTo("CLIENT_APP");
        assertThat(claims.get("scopeLevel")).isEqualTo("PLATFORM");
        assertThat(claims.get("scope")).isEqualTo("api.read api.write");
    }

    @Test
    void humanTokenWithoutRoles_omitsRolesClaim() {
        Jwt jwt = Jwt.withTokenValue("raw")
                .header("alg", "RS256")
                .subject(ACCOUNT)
                .claim("org_id", ORG)
                .claim("space_id", SPACE)
                .claim("account_id", ACCOUNT)
                .claim("user_id", USER)
                .claim("subject_type", "HUMAN")
                .claim("auth_method", "PASSWORD")
                .claim("takibo_scope_level", "SPACE")
                .build();

        Map<String, Object> claims = validate(jwt);

        assertThat(claims).containsEntry("accountId", ACCOUNT)
                .containsEntry("userId", USER)
                .containsEntry("subjectType", "HUMAN")
                .containsEntry("authMethod", "PASSWORD")
                .containsEntry("scopeLevel", "SPACE")
                .doesNotContainKey("roles");
    }

    @Test
    void humanTokenWithEmptyRoles_omitsRolesClaim() {
        Jwt jwt = Jwt.withTokenValue("raw")
                .header("alg", "RS256")
                .subject(ACCOUNT)
                .claim("roles", List.of())
                .claim("subject_type", "HUMAN")
                .claim("auth_method", "PASSWORD")
                .claim("takibo_scope_level", "SPACE")
                .build();

        Map<String, Object> claims = validate(jwt);

        assertThat(claims).doesNotContainKey("roles");
    }
}
