package com.takibo.authorizationserver.infrastructure.springauthserver.authorization;

import com.takibo.authorizationserver.infrastructure.springauthserver.token.TakiboTokenClaims;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.settings.ClientSettings;

import java.util.UUID;

/**
 * Fixtures {@link RegisteredClient} partagées entre {@link JpaOAuth2AuthorizationServiceTest} et
 * {@link JpaOAuth2AuthorizationConsentServiceTest} — extrait pour éliminer la duplication entre
 * les deux classes de test (SonarCloud).
 */
final class TestRegisteredClients {

    private TestRegisteredClients() {
    }

    /**
     * Client SPACE {@code busa-finance} de base, {@code client_credentials} seul. Chaque appelant
     * complète le builder selon ses besoins (grant type supplémentaire, redirect URI, etc.).
     */
    static RegisteredClient.Builder spaceClientBuilder(String registeredClientId, UUID orgId, UUID spaceId) {
        return RegisteredClient.withId(registeredClientId)
                .clientId("busa-finance")
                .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
                .authorizationGrantType(AuthorizationGrantType.CLIENT_CREDENTIALS)
                .scope("api.read")
                .clientSettings(ClientSettings.builder()
                        .setting(TakiboTokenClaims.ORG_ID, orgId.toString())
                        .setting(TakiboTokenClaims.SPACE_ID, spaceId.toString())
                        .build());
    }

    static RegisteredClient platformClient() {
        return RegisteredClient.withId(UUID.randomUUID().toString())
                .clientId("postman-client")
                .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
                .authorizationGrantType(AuthorizationGrantType.CLIENT_CREDENTIALS)
                .scope("api.read")
                .build();
    }
}
