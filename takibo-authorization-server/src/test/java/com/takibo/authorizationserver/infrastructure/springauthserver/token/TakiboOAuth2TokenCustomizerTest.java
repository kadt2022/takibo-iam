package com.takibo.authorizationserver.infrastructure.springauthserver.token;

import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.settings.ClientSettings;

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

    @Test
    void space_client_emits_real_tenant_and_scope() {
        JwtClaimsSet.Builder claims = JwtClaimsSet.builder();
        TakiboOAuth2TokenCustomizer.applyTenantClaims(spaceClient(), claims);
        JwtClaimsSet set = claims.build();

        assertThat(set.getClaimAsString("takibo_scope_level")).isEqualTo("SPACE");
        assertThat(set.getClaimAsString("org_id")).isEqualTo(ORG);
        assertThat(set.getClaimAsString("space_id")).isEqualTo(SPACE);
        assertThat(set.getClaimAsString("takibo_tenant_source")).isEqualTo("oauth2_client");
    }

    @Test
    void platform_client_emits_no_tenant() {
        JwtClaimsSet.Builder claims = JwtClaimsSet.builder();
        TakiboOAuth2TokenCustomizer.applyTenantClaims(platformClient(), claims);
        JwtClaimsSet set = claims.build();

        assertThat(set.getClaimAsString("takibo_scope_level")).isEqualTo("PLATFORM");
        assertThat(set.getClaimAsString("takibo_tenant_source")).isEqualTo("platform_client");
        assertThat((Object) set.getClaim("org_id")).isNull();
        assertThat((Object) set.getClaim("space_id")).isNull();
    }

    @Test
    void no_token_ever_contains_the_stub_org() {
        JwtClaimsSet.Builder claims = JwtClaimsSet.builder();
        TakiboOAuth2TokenCustomizer.applyTenantClaims(platformClient(), claims);
        JwtClaimsSet set = claims.build();

        assertThat(set.getClaims().values()).doesNotContain(STUB_ORG);
    }

    @Test
    void space_client_without_org_or_space_fails_closed() {
        RegisteredClient broken = baseClient("3", "broken-space")
                .clientSettings(ClientSettings.builder()
                        .setting(TakiboTokenClaims.SCOPE_LEVEL, TakiboTokenClaims.SCOPE_SPACE)
                        .build())
                .build();

        assertThatThrownBy(() -> TakiboOAuth2TokenCustomizer.applyTenantClaims(broken, JwtClaimsSet.builder()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("SPACE_CLIENT_REQUIRES_ORG_AND_SPACE");
    }

    @Test
    void subject_claims_set_only_for_client_credentials() {
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
}
