package com.takibo.iamboot.tas;

import com.takibo.authorizationserver.infrastructure.springauthserver.properties.TasAuthorizationServerProperties;
import com.takibo.identitycore.application.auth.port.HumanAccessTokenIssuer;
import com.takibo.identitycore.domain.catalogrbac.TechnicalPermission;
import com.takibo.securitymanagement.infrastructure.security.JwtAuthenticationFilter;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.test.context.ActiveProfiles;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Les permissions d'un token ne sont retenues par TSM que si elles appartiennent au catalogue
 * et au plan du niveau du token. Les tokens sont ici signés directement avec la clé de TAS,
 * sans les contrôles de l'émetteur, pour reproduire un token signé par erreur : seule la
 * validation à la réception est mesurée. Chaîne réelle de bout en bout sur PostgreSQL.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "management.health.mail.enabled=false",
                "security.password-encoder.bcrypt-strength=4"
        })
@ActiveProfiles("test")
@EnabledIf("dockerIsAvailable")
class TokenPermissionBoundaryPostgresIntegrationTest extends TasPostgresBaseline {

    private static final UUID ORG_ID = UUID.fromString("aaaaaaaa-0000-0000-0000-0000000000f8");
    private static final UUID SPACE_ID = UUID.fromString("bbbbbbbb-0000-0000-0000-0000000000f8");
    private static final UUID ACCOUNT_ID = UUID.fromString("cccccccc-0000-0000-0000-0000000000f8");
    private static final UUID USER_ID = UUID.fromString("dddddddd-0000-0000-0000-0000000000f8");
    private static final String ORG_CODE = "perm-boundary-org";
    private static final String SPACE_CODE = "perm-boundary-space";
    private static final String USERS = "/api/v1/orgs/" + ORG_CODE + "/spaces/" + SPACE_CODE + "/users";
    private static final String SPACES = "/api/v1/orgs/" + ORG_ID + "/spaces";

    @LocalServerPort private int port;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private HumanAccessTokenIssuer tokenIssuer;
    @Autowired private JwtEncoder jwtEncoder;
    @Autowired private TasAuthorizationServerProperties tasProperties;
    @Autowired private JwtAuthenticationFilter jwtAuthenticationFilter;

    private TasHttpFixture fixture;

    @BeforeEach
    void seed() {
        fixture = new TasHttpFixture(jdbc, tokenIssuer, port);
        fixture.assertRunsOnPostgres();
        fixture.purgeOrganizations(ORG_CODE);
        fixture.organizationWithSpaceAndAdmin(ORG_ID, ORG_CODE, SPACE_ID, SPACE_CODE, ACCOUNT_ID, USER_ID);
    }

    @AfterEach
    void cleanUp() {
        fixture.purgeOrganizations(ORG_CODE);
    }

    @Test
    void a_legitimate_space_permission_keeps_working() {
        String token = spaceToken(List.of(TechnicalPermission.SPACE_USERS_READ.code()));

        assertThat(fixture.send("GET", USERS, token, Map.of(), null).statusCode()).isEqualTo(200);
    }

    @Test
    void a_permission_outside_the_catalog_never_becomes_an_authority() {
        String token = spaceToken(List.of("USER_READ", TechnicalPermission.SPACE_USERS_READ.code()));

        assertThat(authorities(token)).contains(TechnicalPermission.SPACE_USERS_READ.code())
                .doesNotContain("USER_READ");
    }

    @Test
    void a_permission_outside_the_catalog_opens_no_business_route() {
        String rawOnly = spaceToken(List.of("USER_READ", "SPACE_USERS_READ", "p_space_users_read"));

        assertThat(fixture.send("GET", USERS, rawOnly, Map.of(), null).statusCode())
                .as("permissions brutes, hors catalogue").isEqualTo(403);
        assertThat(authorities(rawOnly)).noneMatch(a -> a.contains("USERS_READ") || a.equals("USER_READ"));
    }

    @Test
    void organization_and_platform_permissions_are_dropped_from_a_space_token() {
        String token = spaceToken(List.of(
                TechnicalPermission.SPACE_USERS_READ.code(),
                TechnicalPermission.ORG_SPACES_READ.code(),
                TechnicalPermission.ORG_USERS_MANAGE.code(),
                "P_PLATFORM_ORGS_READ"));

        assertThat(authorities(token)).contains(TechnicalPermission.SPACE_USERS_READ.code())
                .doesNotContain(TechnicalPermission.ORG_SPACES_READ.code(),
                        TechnicalPermission.ORG_USERS_MANAGE.code(), "P_PLATFORM_ORGS_READ");
        assertThat(fixture.send("GET", SPACES, token, Map.of(), null).statusCode())
                .as("collection des spaces (autorité ORG)").isEqualTo(403);
    }

    @Test
    void a_space_permission_is_dropped_from_an_organization_token() {
        String token = orgToken(List.of(
                TechnicalPermission.ORG_SPACES_READ.code(), TechnicalPermission.SPACE_USERS_READ.code()));

        assertThat(authorities(token)).contains(TechnicalPermission.ORG_SPACES_READ.code())
                .doesNotContain(TechnicalPermission.SPACE_USERS_READ.code());
        assertThat(fixture.send("GET", SPACES, token, Map.of(), null).statusCode())
                .as("collection des spaces avec la permission ORG légitime").isEqualTo(200);
    }

    // ---------- Outils ----------

    /**
     * Autorités vues par Spring Security, telles que l'ADP et les politiques les reçoivent : le
     * vrai {@link JwtAuthenticationFilter} de l'application traite le token (validation, filtres
     * de rôles et de permissions, AuthorityFactory), sans aucun endpoint dédié aux tests.
     */
    private List<String> authorities(String token) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", USERS);
        request.addHeader("Authorization", "Bearer " + token);
        List<String> seen = new ArrayList<>();
        boolean[] authenticated = {false};
        try {
            jwtAuthenticationFilter.doFilter(request, new MockHttpServletResponse(), (req, res) -> {
                var authentication = SecurityContextHolder.getContext().getAuthentication();
                authenticated[0] = authentication != null && authentication.isAuthenticated();
                if (authentication != null) {
                    authentication.getAuthorities().forEach(authority -> seen.add(authority.getAuthority()));
                }
            });
        } catch (ServletException | IOException e) {
            throw new IllegalStateException("Filtre d'authentification en échec", e);
        } finally {
            SecurityContextHolder.clearContext();
        }
        assertThat(authenticated[0]).as("le token doit être authentifié par le filtre").isTrue();
        return seen;
    }

    private String spaceToken(List<String> permissions) {
        return sign(baseClaims(permissions)
                .claim("takibo_scope_level", "SPACE")
                .claim("takibo_tenant_source", "human_space_selection")
                .claim("space_id", SPACE_ID.toString())
                .claim("user_id", USER_ID.toString())
                .build());
    }

    private String orgToken(List<String> permissions) {
        return sign(baseClaims(permissions)
                .claim("takibo_scope_level", "ORGANIZATION")
                .claim("takibo_tenant_source", "human_login")
                .build());
    }

    /** Mêmes claims que l'émetteur humain, sans ses contrôles de cohérence des permissions. */
    private JwtClaimsSet.Builder baseClaims(List<String> permissions) {
        Instant now = Instant.now();
        return JwtClaimsSet.builder()
                .issuer(tasProperties.getIssuer())
                .subject(ACCOUNT_ID.toString())
                .id(UUID.randomUUID().toString())
                .issuedAt(now)
                .expiresAt(now.plusSeconds(300))
                .claim("subject_type", "HUMAN")
                .claim("auth_method", "PASSWORD")
                .claim("org_id", ORG_ID.toString())
                .claim("account_id", ACCOUNT_ID.toString())
                .claim("roles", List.of())
                .claim("groups", List.of())
                .claim("permissions", permissions);
    }

    private String sign(JwtClaimsSet claims) {
        return jwtEncoder.encode(JwtEncoderParameters.from(claims)).getTokenValue();
    }
}
