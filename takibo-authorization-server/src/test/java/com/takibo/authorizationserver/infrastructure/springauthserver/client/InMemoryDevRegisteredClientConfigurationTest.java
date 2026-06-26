package com.takibo.authorizationserver.infrastructure.springauthserver.client;

import com.takibo.authorizationserver.infrastructure.springauthserver.token.TakiboTokenClaims;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;

import static org.assertj.core.api.Assertions.assertThat;

class InMemoryDevRegisteredClientConfigurationTest {

    @Test
    void given_dev_postman_secret_when_platform_repository_is_created_then_platform_client_has_expected_settings() {
        var config = new InMemoryDevRegisteredClientConfiguration();
        var passwordEncoder = new BCryptPasswordEncoder();

        var repository = config.platformRegisteredClientRepository(passwordEncoder, "dev-secret");
        var client = repository.findByClientId("postman-client");

        assertThat(client).isNotNull();
        assertThat(client.getClientSecret()).isNotEqualTo("dev-secret");
        assertThat(passwordEncoder.matches("dev-secret", client.getClientSecret())).isTrue();
        assertThat(client.getClientAuthenticationMethods()).contains(ClientAuthenticationMethod.CLIENT_SECRET_BASIC);
        assertThat(client.getAuthorizationGrantTypes()).contains(AuthorizationGrantType.CLIENT_CREDENTIALS);
        assertThat(client.getScopes()).containsExactlyInAnyOrder("api.read", "api.write");
        assertThat((String) client.getClientSettings().getSetting(TakiboTokenClaims.SCOPE_LEVEL))
                .isEqualTo(TakiboTokenClaims.SCOPE_PLATFORM);
        assertThat((String) client.getClientSettings().getSetting(TakiboTokenClaims.TENANT_SOURCE))
                .isEqualTo(TakiboTokenClaims.SOURCE_PLATFORM);
        assertThat((Object) client.getClientSettings().getSetting(TakiboTokenClaims.ORG_ID)).isNull();
        assertThat((Object) client.getClientSettings().getSetting(TakiboTokenClaims.SPACE_ID)).isNull();
    }
}
