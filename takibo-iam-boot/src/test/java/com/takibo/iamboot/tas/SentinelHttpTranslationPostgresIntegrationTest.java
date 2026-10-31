package com.takibo.iamboot.tas;

import com.fasterxml.jackson.databind.JsonNode;
import com.takibo.identitycore.application.auth.port.HumanAccessTokenIssuer;
import com.takibo.identitycore.domain.catalogrbac.TechnicalPermission;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Traduction HTTP des erreurs du socle web : une requête que la politique autorise mais que
 * Spring MVC ne peut pas servir reçoit le statut qui la décrit (405, 415, 406, 404, 400), dans
 * le format Sentinel, jamais un 500 ni un message interne.
 * <p>
 * Chaîne réelle de bout en bout sur PostgreSQL, avec un token autorisé sur la surface visée :
 * chaque réponse vient donc de Spring MVC, pas d'un refus de la politique.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "management.health.mail.enabled=false",
                "security.password-encoder.bcrypt-strength=4"
        })
@ActiveProfiles("test")
@EnabledIf("dockerIsAvailable")
class SentinelHttpTranslationPostgresIntegrationTest extends TasPostgresBaseline {

    private static final UUID ORG_ID = UUID.fromString("aaaaaaaa-0000-0000-0000-0000000000f2");
    private static final UUID SPACE_ID = UUID.fromString("bbbbbbbb-0000-0000-0000-0000000000f2");
    private static final UUID ADMIN_ACCOUNT = UUID.fromString("cccccccc-0000-0000-0000-0000000000f2");
    private static final UUID ADMIN_USER = UUID.fromString("dddddddd-0000-0000-0000-0000000000f2");

    private static final String ORG_CODE = "sentinel-org";
    private static final String USERS = "/api/v1/orgs/" + ORG_CODE + "/spaces/sentinel-space/users";

    @LocalServerPort private int port;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private HumanAccessTokenIssuer tokenIssuer;

    private TasHttpFixture fixture;
    private String token;

    @BeforeEach
    void seed() {
        fixture = new TasHttpFixture(jdbc, tokenIssuer, port);
        fixture.assertRunsOnPostgres();
        fixture.purgeOrganizations(ORG_CODE);
        fixture.organizationWithSpaceAndAdmin(ORG_ID, ORG_CODE, SPACE_ID, "sentinel-space", ADMIN_ACCOUNT, ADMIN_USER);
        token = fixture.spaceToken(ORG_ID, SPACE_ID, ADMIN_ACCOUNT, ADMIN_USER, List.of(
                TechnicalPermission.SPACE_USERS_READ.code(), TechnicalPermission.SPACE_USERS_MANAGE.code()));
    }

    @AfterEach
    void cleanUp() {
        fixture.purgeOrganizations(ORG_CODE);
    }

    @Test
    void given_a_method_without_endpoint_when_the_policy_allows_it_then_405() {
        HttpResponse<String> response = fixture.send("PUT", USERS + "/" + ADMIN_USER, token,
                Map.of("Content-Type", "application/json"), "{}");

        assertSentinel(response, 405, "METHOD_NOT_ALLOWED");
    }

    @Test
    void given_an_unsupported_content_type_when_creating_a_user_then_415() {
        HttpResponse<String> response = fixture.send("POST", USERS, token,
                Map.of("Content-Type", "text/plain"), "not json");

        assertSentinel(response, 415, "UNSUPPORTED_MEDIA_TYPE");
    }

    @Test
    void given_an_unacceptable_media_type_when_listing_users_then_406() {
        HttpResponse<String> response = fixture.send("GET", USERS, token, Map.of("Accept", "application/xml"), null);

        assertSentinel(response, 406, "NOT_ACCEPTABLE");
    }

    @Test
    void given_an_unknown_api_route_when_an_authenticated_caller_requests_it_then_404() {
        HttpResponse<String> response = fixture.send("GET", "/api/v1/route-inexistante", token, Map.of(), null);

        // NotFoundAdvice (iam-boot) répond 404 sans corps à toute ressource inexistante, UI comme API.
        assertThat(response.statusCode()).as("réponse brute : %s", response.body()).isEqualTo(404);
        assertThat(response.body()).doesNotContain("Exception").doesNotContain("org.springframework");
    }

    @Test
    void given_an_unknown_enum_value_in_a_query_parameter_then_400_without_internal_detail() {
        HttpResponse<String> response = fixture.send("GET", USERS + "?status=BOGUS", token, Map.of(), null);

        assertSentinel(response, 400, "BAD_REQUEST");
    }

    @Test
    void given_a_non_numeric_page_parameter_then_400_without_internal_detail() {
        HttpResponse<String> response = fixture.send("GET", USERS + "?page=abc", token, Map.of(), null);

        assertSentinel(response, 400, "BAD_REQUEST");
    }

    private static void assertSentinel(HttpResponse<String> response, int status, String code) {
        assertThat(response.statusCode()).as("réponse brute : %s", response.body()).isEqualTo(status);
        JsonNode body = TasHttpFixture.body(response);
        assertThat(body.path("status").asInt()).isEqualTo(status);
        assertThat(body.path("code").asText()).as("réponse brute : %s", response.body()).isEqualTo(code);
        assertThat(body.has("traceId")).isTrue();
        // Rien du fonctionnement interne : ni classe, ni package, ni message de conversion brut.
        assertThat(response.body())
                .doesNotContain("com.takibo")
                .doesNotContain("org.springframework")
                .doesNotContain("No enum constant")
                .doesNotContain("For input string")
                .doesNotContain("Exception");
    }
}
