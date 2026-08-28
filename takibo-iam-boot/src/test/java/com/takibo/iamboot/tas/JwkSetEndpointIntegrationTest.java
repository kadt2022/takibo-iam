package com.takibo.iamboot.tas;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Ce que {@code /oauth2/jwks} publie, et surtout ce qu'il ne publie pas (TAS-GRANTS-02A).
 * <p>
 * Le {@code JWKSource} rend la cle emettrice <b>avec</b> sa matiere privee : c'est ainsi que
 * l'encodeur signe. L'endpoint, lui, ne doit exposer que la partie publique. Spring s'en
 * charge en serialisant le {@code JWKSet} sous sa forme publique, mais s'appuyer sur ce
 * comportement sans le verifier reviendrait a faire reposer la confidentialite de la cle
 * privee sur une lecture de documentation.
 * <p>
 * Le test tourne sur la source ephemere du profil de test, qui expose elle aussi une cle
 * privee : la garantie verifiee porte sur l'endpoint, pas sur l'origine des cles.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "management.health.mail.enabled=false",
                "security.password-encoder.bcrypt-strength=4"
        })
@ActiveProfiles("test")
@EnabledIf("dockerIsAvailable")
class JwkSetEndpointIntegrationTest extends TasPostgresBaseline {

    /** Parametres prives d'un JWK, RFC 7517 et RFC 7518. Aucun ne doit sortir. */
    private static final List<String> PRIVATE_PARAMETERS =
            List.of("d", "p", "q", "dp", "dq", "qi", "oth", "k");

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final HttpClient HTTP = HttpClient.newHttpClient();

    @LocalServerPort private int port;

    @Test
    void given_the_jwk_set_endpoint_then_it_publishes_at_least_one_key() {
        JsonNode keys = jwkSet().path("keys");

        assertThat(keys.isArray()).isTrue();
        assertThat(keys).isNotEmpty();
    }

    @Test
    void given_the_jwk_set_endpoint_then_no_private_parameter_is_ever_exposed() {
        JsonNode keys = jwkSet().path("keys");

        for (JsonNode key : keys) {
            for (String parameter : PRIVATE_PARAMETERS) {
                assertThat(key.has(parameter))
                        .as("le JWKS ne doit jamais exposer le parametre prive '%s'", parameter)
                        .isFalse();
            }
        }
    }

    @Test
    void given_the_jwk_set_endpoint_then_each_key_keeps_what_a_verifier_needs() {
        // Contre-epreuve du test precedent : retirer le prive ne doit pas retirer le reste,
        // sans quoi la verification deviendrait impossible et le test ci-dessus passerait
        // pour une mauvaise raison.
        JsonNode keys = jwkSet().path("keys");

        for (JsonNode key : keys) {
            assertThat(key.path("kty").asText()).isNotEmpty();
            assertThat(key.path("kid").asText()).isNotEmpty();
            assertThat(key.path("n").asText()).isNotEmpty();
            assertThat(key.path("e").asText()).isNotEmpty();
        }
    }

    private JsonNode jwkSet() {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + "/oauth2/jwks"))
                .GET()
                .build();
        try {
            HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());
            assertThat(response.statusCode()).isEqualTo(200);
            return JSON.readTree(response.body());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Appel /oauth2/jwks interrompu", e);
        } catch (Exception e) {
            throw new IllegalStateException("Appel /oauth2/jwks en echec", e);
        }
    }
}
