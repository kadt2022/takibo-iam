package com.takibo.authorizationserver.infrastructure.springauthserver.client;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.server.authorization.client.InMemoryRegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;

import java.util.UUID;

@Configuration
public class InMemoryDevRegisteredClientConfiguration {

    // Temporary dev client - will be replaced by the Takibo/TMS registered client adapter
    @Bean
    public RegisteredClientRepository registeredClientRepository(PasswordEncoder passwordEncoder) {
        RegisteredClient postmanClient = RegisteredClient.withId(UUID.randomUUID().toString())
                .clientId("postman-client")
                .clientSecret(passwordEncoder.encode("postman-secret"))
                .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
                .authorizationGrantType(AuthorizationGrantType.CLIENT_CREDENTIALS)
                .scope("api.read")
                .scope("api.write")
                .build();

        return new InMemoryRegisteredClientRepository(postmanClient);
    }
}
