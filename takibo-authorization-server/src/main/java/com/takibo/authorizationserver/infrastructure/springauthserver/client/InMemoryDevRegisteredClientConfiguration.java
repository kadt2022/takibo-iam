package com.takibo.authorizationserver.infrastructure.springauthserver.client;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.server.authorization.client.InMemoryRegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.settings.ClientSettings;
import com.takibo.authorizationserver.infrastructure.springauthserver.token.TakiboTokenClaims;

import java.util.UUID;

@Configuration
public class InMemoryDevRegisteredClientConfiguration {

    // Client PLATFORM (bootstrap/signup/dev). Volontairement SANS org_id/space_id :
    // le token émis ne porte aucun tenant -> fail-closed sur les routes tenant.
    @Bean
    public InMemoryRegisteredClientRepository platformRegisteredClientRepository(
            PasswordEncoder passwordEncoder,
            @Value("${takibo.dev.postman-client.secret}") String postmanClientSecret
    ) {
        RegisteredClient postmanClient = RegisteredClient.withId(UUID.randomUUID().toString())
                .clientId("postman-client")
                .clientSecret(passwordEncoder.encode(postmanClientSecret))
                .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
                .authorizationGrantType(AuthorizationGrantType.CLIENT_CREDENTIALS)
                .scope("api.read")
                .scope("api.write")
                .clientSettings(ClientSettings.builder()
                        .setting(TakiboTokenClaims.SCOPE_LEVEL, TakiboTokenClaims.SCOPE_PLATFORM)
                        .setting(TakiboTokenClaims.TENANT_SOURCE, TakiboTokenClaims.SOURCE_PLATFORM)
                        .build())
                .build();

        return new InMemoryRegisteredClientRepository(postmanClient);
    }
}
