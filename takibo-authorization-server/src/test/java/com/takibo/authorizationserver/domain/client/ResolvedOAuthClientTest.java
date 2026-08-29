package com.takibo.authorizationserver.domain.client;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * Invariants de {@link ResolvedOAuthClient} (TAS-GRANTS-01).
 * <p>
 * Le TAS ne fabrique aucune frontiere : un plan et une frontiere incompatibles sont une
 * erreur de configuration, jamais une valeur comblee. Ces tests fixent la matrice complete
 * des trois plans, plus les refus qui empechent un client inutilisable d'atteindre Spring
 * Authorization Server et d'y produire une erreur opaque.
 */
class ResolvedOAuthClientTest {

    private static final UUID ORG = UUID.fromString("674b889c-4d4e-47bd-bdf6-972dc84f1b49");
    private static final UUID SPACE = UUID.fromString("8932f9bc-0af0-4c64-94c8-abb0150c348b");

    // ---------- Matrice plan / frontiere ----------

    @Test
    void given_platform_plan_without_tenant_then_accepted() {
        ResolvedOAuthClient client = aClient().plan(ClientPlan.PLATFORM).org(null).space(null).build();

        assertThat(client.plan()).isEqualTo(ClientPlan.PLATFORM);
        assertThat(client.orgId()).isNull();
        assertThat(client.spaceId()).isNull();
    }

    @Test
    void given_platform_plan_with_organization_then_rejected() {
        assertThatThrownBy(() -> aClient().plan(ClientPlan.PLATFORM).org(ORG).space(null).build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("PLATFORM_CLIENT_MUST_NOT_CARRY_ORGANIZATION");
    }

    @Test
    void given_platform_plan_with_space_then_rejected() {
        assertThatThrownBy(() -> aClient().plan(ClientPlan.PLATFORM).org(null).space(SPACE).build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("CLIENT_PLAN_MUST_NOT_CARRY_SPACE");
    }

    @Test
    void given_organization_plan_with_organization_only_then_accepted() {
        ResolvedOAuthClient client =
                aClient().plan(ClientPlan.ORGANIZATION).org(ORG).space(null).build();

        assertThat(client.orgId()).isEqualTo(ORG);
        assertThat(client.spaceId()).isNull();
    }

    @Test
    void given_organization_plan_without_organization_then_rejected() {
        assertThatThrownBy(() -> aClient().plan(ClientPlan.ORGANIZATION).org(null).space(null).build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("CLIENT_PLAN_REQUIRES_ORGANIZATION");
    }

    @Test
    void given_organization_plan_with_space_then_rejected() {
        assertThatThrownBy(() -> aClient().plan(ClientPlan.ORGANIZATION).org(ORG).space(SPACE).build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("CLIENT_PLAN_MUST_NOT_CARRY_SPACE");
    }

    @Test
    void given_space_plan_with_both_then_accepted() {
        ResolvedOAuthClient client = aClient().plan(ClientPlan.SPACE).org(ORG).space(SPACE).build();

        assertThat(client.orgId()).isEqualTo(ORG);
        assertThat(client.spaceId()).isEqualTo(SPACE);
    }

    @Test
    void given_space_plan_without_space_then_rejected() {
        assertThatThrownBy(() -> aClient().plan(ClientPlan.SPACE).org(ORG).space(null).build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("CLIENT_PLAN_REQUIRES_SPACE");
    }

    @Test
    void given_space_plan_without_organization_then_rejected() {
        assertThatThrownBy(() -> aClient().plan(ClientPlan.SPACE).org(null).space(SPACE).build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("CLIENT_PLAN_REQUIRES_ORGANIZATION");
    }

    // ---------- Identite ----------

    @Test
    void given_blank_technical_id_then_rejected() {
        assertThatThrownBy(() -> aClient().registeredClientId("  ").build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("RESOLVED_CLIENT_REQUIRES_TECHNICAL_ID");
    }

    @Test
    void given_blank_public_client_id_then_rejected() {
        assertThatThrownBy(() -> aClient().clientId(null).build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("RESOLVED_CLIENT_REQUIRES_CLIENT_ID");
    }

    @Test
    void given_missing_plan_then_rejected() {
        assertThatThrownBy(() -> aClient().plan(null).build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("RESOLVED_CLIENT_REQUIRES_PLAN");
    }

    // ---------- Utilisabilite ----------

    @Test
    void given_no_grant_type_then_rejected() {
        // Un client sans grant type est inutilisable. Le refuser ici plutot que de laisser
        // Spring Authorization Server echouer sur une construction opaque.
        assertThatThrownBy(() -> aClient().grantTypes(Set.of()).build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("CLIENT_REQUIRES_AT_LEAST_ONE_GRANT_TYPE");
    }

    @Test
    void given_null_grant_types_then_rejected() {
        assertThatThrownBy(() -> aClient().grantTypes(null).build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("CLIENT_REQUIRES_AT_LEAST_ONE_GRANT_TYPE");
    }

    @Test
    void given_missing_token_endpoint_auth_method_then_rejected() {
        assertThatThrownBy(() -> aClient().tokenEndpointAuthMethod(" ").build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("CLIENT_REQUIRES_TOKEN_ENDPOINT_AUTH_METHOD");
    }

    // ---------- Secret ----------

    @Test
    void given_secret_required_without_hash_then_rejected() {
        assertThatThrownBy(() -> aClient().requireClientSecret(true).secretHash(null).build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("CLIENT_REQUIRES_SECRET_HASH");
    }

    @Test
    void given_public_client_requiring_a_secret_then_rejected() {
        assertThatThrownBy(() -> aClient()
                .clientType(ClientType.PUBLIC)
                .requireClientSecret(true)
                .secretHash("$2a$12$hash")
                .build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("PUBLIC_CLIENT_MUST_NOT_REQUIRE_SECRET");
    }

    @Test
    void given_public_client_without_secret_then_accepted() {
        assertThatCode(() -> aClient()
                .clientType(ClientType.PUBLIC)
                .requireClientSecret(false)
                .secretHash(null)
                .build())
                .doesNotThrowAnyException();
    }

    // ---------- PKCE : la regle decoule du client, pas de la requete ----------

    @Test
    void given_public_client_then_pkce_is_required_even_when_not_configured() {
        ResolvedOAuthClient client = aClient()
                .clientType(ClientType.PUBLIC)
                .requireClientSecret(false)
                .secretHash(null)
                .requireProofKey(false)
                .build();

        assertThat(client.pkceRequired()).isTrue();
    }

    @Test
    void given_confidential_client_configured_for_pkce_then_pkce_is_required() {
        assertThat(aClient().requireProofKey(true).build().pkceRequired()).isTrue();
    }

    @Test
    void given_confidential_client_without_pkce_then_pkce_is_not_required() {
        assertThat(aClient().requireProofKey(false).build().pkceRequired()).isFalse();
    }

    // ---------- Durees de vie ----------

    @Test
    void given_null_ttls_then_accepted_and_left_to_the_authorization_server_defaults() {
        ResolvedOAuthClient client = aClient()
                .accessTokenTtl(null).refreshTokenTtl(null).idTokenTtl(null).build();

        assertThat(client.accessTokenTtl()).isNull();
        assertThat(client.refreshTokenTtl()).isNull();
        assertThat(client.idTokenTtl()).isNull();
    }

    @Test
    void given_zero_ttl_then_rejected() {
        assertThatThrownBy(() -> aClient().accessTokenTtl(Duration.ZERO).build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("ACCESS_TOKEN_TTL_MUST_BE_POSITIVE");
    }

    @Test
    void given_negative_ttl_then_rejected() {
        assertThatThrownBy(() -> aClient().refreshTokenTtl(Duration.ofSeconds(-1)).build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("REFRESH_TOKEN_TTL_MUST_BE_POSITIVE");
    }

    // ---------- Collections ----------

    @Test
    void given_null_optional_collections_then_normalised_to_empty() {
        ResolvedOAuthClient client =
                aClient().scopes(null).redirectUris(null).postLogoutRedirectUris(null).build();

        assertThat(client.scopes()).isEmpty();
        assertThat(client.redirectUris()).isEmpty();
        assertThat(client.postLogoutRedirectUris()).isEmpty();
    }

    @Test
    void given_a_resolved_client_then_its_collections_are_immutable() {
        ResolvedOAuthClient client = aClient().build();

        assertThatThrownBy(() -> client.scopes().add("api.write"))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> client.grantTypes().add("refresh_token"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void given_a_resolved_client_then_grant_type_support_is_answered_from_it() {
        ResolvedOAuthClient client =
                aClient().grantTypes(Set.of("client_credentials", "refresh_token")).build();

        assertThat(client.supportsGrantType("client_credentials")).isTrue();
        assertThat(client.supportsGrantType("authorization_code")).isFalse();
    }

    // ---------- Constructeur d'essai ----------

    private static Builder aClient() {
        return new Builder();
    }

    /** Un client SPACE valide par defaut ; chaque test ne declare que ce qu'il fait varier. */
    private static final class Builder {
        private String registeredClientId = "11111111-1111-1111-1111-111111111111";
        private String clientId = "busa-finance";
        private ClientPlan plan = ClientPlan.SPACE;
        private UUID orgId = ORG;
        private UUID spaceId = SPACE;
        private ClientType clientType = ClientType.CONFIDENTIAL;
        private boolean requireProofKey = false;
        private boolean requireConsent = false;
        private boolean requireClientSecret = true;
        private String clientSecretHash = "$2a$12$hashvalue";
        private String tokenEndpointAuthMethod = "client_secret_basic";
        private Duration accessTokenTtl = null;
        private Duration refreshTokenTtl = null;
        private Duration idTokenTtl = null;
        private Set<String> scopes = Set.of("api.read");
        private Set<String> grantTypes = Set.of("client_credentials");
        private Set<String> redirectUris = Set.of();
        private Set<String> postLogoutRedirectUris = Set.of();

        Builder registeredClientId(String v) { this.registeredClientId = v; return this; }
        Builder clientId(String v) { this.clientId = v; return this; }
        Builder plan(ClientPlan v) { this.plan = v; return this; }
        Builder org(UUID v) { this.orgId = v; return this; }
        Builder space(UUID v) { this.spaceId = v; return this; }
        Builder clientType(ClientType v) { this.clientType = v; return this; }
        Builder requireProofKey(boolean v) { this.requireProofKey = v; return this; }
        Builder requireClientSecret(boolean v) { this.requireClientSecret = v; return this; }
        Builder secretHash(String v) { this.clientSecretHash = v; return this; }
        Builder tokenEndpointAuthMethod(String v) { this.tokenEndpointAuthMethod = v; return this; }
        Builder accessTokenTtl(Duration v) { this.accessTokenTtl = v; return this; }
        Builder refreshTokenTtl(Duration v) { this.refreshTokenTtl = v; return this; }
        Builder idTokenTtl(Duration v) { this.idTokenTtl = v; return this; }
        Builder scopes(Set<String> v) { this.scopes = v; return this; }
        Builder grantTypes(Set<String> v) { this.grantTypes = v; return this; }
        Builder redirectUris(Set<String> v) { this.redirectUris = v; return this; }
        Builder postLogoutRedirectUris(Set<String> v) { this.postLogoutRedirectUris = v; return this; }

        ResolvedOAuthClient build() {
            return new ResolvedOAuthClient(
                    registeredClientId, clientId, plan, orgId, spaceId, clientType,
                    requireProofKey, requireConsent, requireClientSecret, clientSecretHash,
                    tokenEndpointAuthMethod, null, null, null,
                    accessTokenTtl, refreshTokenTtl, idTokenTtl,
                    scopes, grantTypes, redirectUris, postLogoutRedirectUris);
        }
    }
}
