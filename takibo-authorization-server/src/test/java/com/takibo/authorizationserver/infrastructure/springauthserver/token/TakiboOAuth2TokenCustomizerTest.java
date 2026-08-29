package com.takibo.authorizationserver.infrastructure.springauthserver.token;

import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.core.oidc.endpoint.OidcParameterNames;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.settings.ClientSettings;
import org.springframework.security.oauth2.server.authorization.settings.TokenSettings;
import org.springframework.security.oauth2.server.authorization.token.JwtEncodingContext;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TakiboOAuth2TokenCustomizerTest {

    private static final String ORG = "674b889c-4d4e-47bd-bdf6-972dc84f1b49";
    private static final String SPACE = "8932f9bc-0af0-4c64-94c8-abb0150c348b";
    private static final String STUB_ORG = "00000000-0000-0000-0000-000000000001";

    private RegisteredClient.Builder baseClient(String id, String clientId) {
        return RegisteredClient.withId(id)
                .clientId(clientId)
                .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
                .authorizationGrantType(AuthorizationGrantType.CLIENT_CREDENTIALS);
    }

    private RegisteredClient spaceClient() {
        return baseClient("1", "busa-finance")
                .clientSettings(ClientSettings.builder()
                        .setting(TakiboTokenClaims.SCOPE_LEVEL, TakiboTokenClaims.SCOPE_SPACE)
                        .setting(TakiboTokenClaims.TENANT_SOURCE, TakiboTokenClaims.SOURCE_OAUTH2_CLIENT)
                        .setting(TakiboTokenClaims.ORG_ID, ORG)
                        .setting(TakiboTokenClaims.SPACE_ID, SPACE)
                        .build())
                .build();
    }

    private RegisteredClient platformClient() {
        return baseClient("2", "postman-client")
                .clientSettings(ClientSettings.builder()
                        .setting(TakiboTokenClaims.SCOPE_LEVEL, TakiboTokenClaims.SCOPE_PLATFORM)
                        .setting(TakiboTokenClaims.TENANT_SOURCE, TakiboTokenClaims.SOURCE_PLATFORM)
                        .build())
                .build();
    }

    private RegisteredClient organizationClient() {
        return baseClient("4", "organization-client")
                .clientSettings(ClientSettings.builder()
                        .setting(TakiboTokenClaims.SCOPE_LEVEL,
                                TakiboTokenClaims.SCOPE_ORGANIZATION)
                        .setting(TakiboTokenClaims.TENANT_SOURCE,
                                TakiboTokenClaims.SOURCE_OAUTH2_CLIENT)
                        .setting(TakiboTokenClaims.ORG_ID, ORG)
                        .build())
                .build();
    }

    @Test
    void given_space_client_when_apply_tenant_claims_then_emits_real_tenant_and_scope() {
        JwtClaimsSet.Builder claims = JwtClaimsSet.builder();
        TakiboOAuth2TokenCustomizer.applyTenantClaims(spaceClient(), claims);
        JwtClaimsSet set = claims.build();

        assertThat(set.getClaimAsString("takibo_scope_level")).isEqualTo("SPACE");
        assertThat(set.getClaimAsString("org_id")).isEqualTo(ORG);
        assertThat(set.getClaimAsString("space_id")).isEqualTo(SPACE);
        assertThat(set.getClaimAsString("takibo_tenant_source")).isEqualTo("oauth2_client");
    }

    @Test
    void given_platform_client_when_apply_tenant_claims_then_emits_no_tenant() {
        JwtClaimsSet.Builder claims = JwtClaimsSet.builder();
        TakiboOAuth2TokenCustomizer.applyTenantClaims(platformClient(), claims);
        JwtClaimsSet set = claims.build();

        assertThat(set.getClaimAsString("takibo_scope_level")).isEqualTo("PLATFORM");
        assertThat(set.getClaimAsString("takibo_tenant_source")).isEqualTo("platform_client");
        assertThat((Object) set.getClaim("org_id")).isNull();
        assertThat((Object) set.getClaim("space_id")).isNull();
    }

    @Test
    void given_organization_client_when_apply_tenant_claims_then_emits_org_without_space() {
        JwtClaimsSet.Builder claims = JwtClaimsSet.builder();
        TakiboOAuth2TokenCustomizer.applyTenantClaims(organizationClient(), claims);
        JwtClaimsSet set = claims.build();

        assertThat(set.getClaimAsString("takibo_scope_level")).isEqualTo("ORGANIZATION");
        assertThat(set.getClaimAsString("org_id")).isEqualTo(ORG);
        assertThat((Object) set.getClaim("space_id")).isNull();
        assertThat(set.getClaimAsString("takibo_tenant_source")).isEqualTo("oauth2_client");
    }

    @Test
    void given_platform_client_when_apply_tenant_claims_then_no_token_contains_stub_org() {
        JwtClaimsSet.Builder claims = JwtClaimsSet.builder();
        TakiboOAuth2TokenCustomizer.applyTenantClaims(platformClient(), claims);
        JwtClaimsSet set = claims.build();

        assertThat(set.getClaims()).isNotEmpty();
        assertThat(set.getClaims().values()).doesNotContain(STUB_ORG);
    }

    @Test
    void given_space_client_without_org_or_space_when_apply_tenant_claims_then_fails_closed() {
        RegisteredClient broken = baseClient("3", "broken-space")
                .clientSettings(ClientSettings.builder()
                        .setting(TakiboTokenClaims.SCOPE_LEVEL, TakiboTokenClaims.SCOPE_SPACE)
                        .setting(TakiboTokenClaims.TENANT_SOURCE,
                                TakiboTokenClaims.SOURCE_OAUTH2_CLIENT)
                        .build())
                .build();

        assertThatThrownBy(() -> TakiboOAuth2TokenCustomizer.applyTenantClaims(broken, JwtClaimsSet.builder()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("SPACE_CLIENT_REQUIRES_ORG_AND_SPACE");
    }

    @Test
    void given_platform_client_with_tenant_when_apply_tenant_claims_then_fails_closed() {
        RegisteredClient broken = baseClient("5", "broken-platform")
                .clientSettings(ClientSettings.builder()
                        .setting(TakiboTokenClaims.SCOPE_LEVEL, TakiboTokenClaims.SCOPE_PLATFORM)
                        .setting(TakiboTokenClaims.TENANT_SOURCE,
                                TakiboTokenClaims.SOURCE_PLATFORM)
                        .setting(TakiboTokenClaims.ORG_ID, ORG)
                        .build())
                .build();

        assertThatThrownBy(() -> TakiboOAuth2TokenCustomizer.applyTenantClaims(
                broken, JwtClaimsSet.builder()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("PLATFORM_CLIENT_MUST_NOT_CARRY_TENANT");
    }

    @Test
    void given_client_without_explicit_scope_or_source_then_fails_closed() {
        RegisteredClient missingScope = baseClient("6", "missing-scope")
                .clientSettings(ClientSettings.builder()
                        .setting(TakiboTokenClaims.TENANT_SOURCE,
                                TakiboTokenClaims.SOURCE_PLATFORM)
                        .build())
                .build();
        RegisteredClient missingSource = baseClient("7", "missing-source")
                .clientSettings(ClientSettings.builder()
                        .setting(TakiboTokenClaims.SCOPE_LEVEL, TakiboTokenClaims.SCOPE_PLATFORM)
                        .build())
                .build();

        assertThatThrownBy(() -> TakiboOAuth2TokenCustomizer.applyTenantClaims(
                missingScope, JwtClaimsSet.builder()))
                .hasMessageContaining("CLIENT_REQUIRES_EXPLICIT_SCOPE_LEVEL");
        assertThatThrownBy(() -> TakiboOAuth2TokenCustomizer.applyTenantClaims(
                missingSource, JwtClaimsSet.builder()))
                .hasMessageContaining("CLIENT_REQUIRES_EXPLICIT_TENANT_SOURCE");
    }

    @Test
    void given_client_credentials_grant_when_apply_subject_claims_then_sets_client_subject_claims() {
        JwtClaimsSet.Builder cc = JwtClaimsSet.builder().subject("x");
        TakiboOAuth2TokenCustomizer.applySubjectClaims(AuthorizationGrantType.CLIENT_CREDENTIALS, cc);
        JwtClaimsSet ccSet = cc.build();
        assertThat(ccSet.getClaimAsString("subject_type")).isEqualTo("CLIENT_APP");
        assertThat(ccSet.getClaimAsString("auth_method")).isEqualTo("OAUTH2_CLIENT_CREDENTIALS");

        JwtClaimsSet.Builder ac = JwtClaimsSet.builder().subject("x");
        TakiboOAuth2TokenCustomizer.applySubjectClaims(AuthorizationGrantType.AUTHORIZATION_CODE, ac);
        JwtClaimsSet acSet = ac.build();
        assertThat((Object) acSet.getClaim("subject_type")).isNull();
        assertThat((Object) acSet.getClaim("auth_method")).isNull();
    }

    @Test
    void given_access_token_context_when_token_customizer_runs_then_applies_tenant_and_subject_claims() {
        JwtClaimsSet.Builder claims = JwtClaimsSet.builder().subject("busa-finance");
        JwtEncodingContext context = jwtContext(OAuth2TokenType.ACCESS_TOKEN, spaceClient(), claims);

        new TakiboOAuth2TokenCustomizer().tokenCustomizer(Clock.systemUTC()).customize(context);
        JwtClaimsSet set = claims.build();

        assertThat(set.getClaimAsString("takibo_scope_level")).isEqualTo("SPACE");
        assertThat(set.getClaimAsString("org_id")).isEqualTo(ORG);
        assertThat(set.getClaimAsString("space_id")).isEqualTo(SPACE);
        assertThat(set.getClaimAsString("subject_type")).isEqualTo("CLIENT_APP");
        assertThat(set.getClaimAsString("auth_method")).isEqualTo("OAUTH2_CLIENT_CREDENTIALS");
    }

    @Test
    void given_non_access_token_context_when_token_customizer_runs_then_claims_are_unchanged() {
        JwtClaimsSet.Builder claims = JwtClaimsSet.builder().subject("busa-finance");
        JwtEncodingContext context = jwtContext(OAuth2TokenType.REFRESH_TOKEN, spaceClient(), claims);

        new TakiboOAuth2TokenCustomizer().tokenCustomizer(Clock.systemUTC()).customize(context);
        JwtClaimsSet set = claims.build();

        assertThat((Object) set.getClaim("takibo_scope_level")).isNull();
        assertThat((Object) set.getClaim("org_id")).isNull();
        assertThat((Object) set.getClaim("space_id")).isNull();
        assertThat((Object) set.getClaim("subject_type")).isNull();
    }

    @Test
    void given_an_id_token_context_with_a_configured_ttl_then_expiry_is_rewritten_to_that_ttl() {
        RegisteredClient client = baseClient("8", "web-app")
                .tokenSettings(TokenSettings.builder()
                        .setting(TakiboTokenClaims.ID_TOKEN_TTL_SECONDS, 600L)
                        .build())
                .build();
        JwtClaimsSet.Builder claims = JwtClaimsSet.builder()
                .subject("busa-finance")
                .expiresAt(Instant.parse("2026-08-29T12:30:00Z")); // 30 min fixes de SAS
        Clock fixedClock = Clock.fixed(Instant.parse("2026-08-29T12:00:00Z"), ZoneOffset.UTC);
        JwtEncodingContext context = jwtContext(
                new OAuth2TokenType(OidcParameterNames.ID_TOKEN), client, claims);

        new TakiboOAuth2TokenCustomizer().tokenCustomizer(fixedClock).customize(context);
        JwtClaimsSet set = claims.build();

        assertThat(set.getExpiresAt()).isEqualTo(Instant.parse("2026-08-29T12:10:00Z"));
    }

    @Test
    void given_an_id_token_context_without_a_configured_ttl_then_expiry_is_left_untouched() {
        RegisteredClient client = baseClient("9", "web-app").build();
        Instant fixedExpiry = Instant.parse("2026-08-29T12:30:00Z");
        JwtClaimsSet.Builder claims = JwtClaimsSet.builder()
                .subject("busa-finance")
                .expiresAt(fixedExpiry);
        Clock clock = Clock.fixed(Instant.parse("2026-08-29T12:00:00Z"), ZoneOffset.UTC);
        JwtEncodingContext context = jwtContext(
                new OAuth2TokenType(OidcParameterNames.ID_TOKEN), client, claims);

        new TakiboOAuth2TokenCustomizer().tokenCustomizer(clock).customize(context);
        JwtClaimsSet set = claims.build();

        assertThat(set.getExpiresAt()).isEqualTo(fixedExpiry);
    }

    @Test
    void given_an_id_token_context_then_tenant_and_subject_claims_are_not_applied() {
        // L'ID token n'est pas un access token : la branche TTL retourne avant applyTenantClaims.
        RegisteredClient client = spaceClient();
        JwtClaimsSet.Builder claims = JwtClaimsSet.builder().subject("busa-finance");
        Clock clock = Clock.fixed(Instant.parse("2026-08-29T12:00:00Z"), ZoneOffset.UTC);
        JwtEncodingContext context = jwtContext(
                new OAuth2TokenType(OidcParameterNames.ID_TOKEN), client, claims);

        new TakiboOAuth2TokenCustomizer().tokenCustomizer(clock).customize(context);
        JwtClaimsSet set = claims.build();

        assertThat((Object) set.getClaim("takibo_scope_level")).isNull();
        assertThat((Object) set.getClaim("subject_type")).isNull();
    }

    private JwtEncodingContext jwtContext(
            OAuth2TokenType tokenType,
            RegisteredClient registeredClient,
            JwtClaimsSet.Builder claims) {
        return JwtEncodingContext.with(JwsHeader.with(SignatureAlgorithm.RS256), claims)
                .tokenType(tokenType)
                .registeredClient(registeredClient)
                .authorizationGrantType(AuthorizationGrantType.CLIENT_CREDENTIALS)
                .build();
    }
}
