package com.takibo.authorizationserver.infrastructure.springauthserver.client;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.context.AuthorizationServerContext;
import org.springframework.security.oauth2.server.authorization.context.AuthorizationServerContextHolder;
import org.springframework.security.oauth2.server.authorization.settings.AuthorizationServerSettings;
import org.springframework.security.oauth2.server.authorization.settings.ClientSettings;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Instant;
import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TakiboJwtClientAssertionDecoderFactoryTest {

    private static final String ISSUER = "https://id.example";

    @AfterEach
    void clearAuthorizationServerContext() {
        AuthorizationServerContextHolder.resetContext();
    }

    @Test
    void decodes_client_assertion_with_embedded_public_jwk_set() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        KeyPair keyPair = generator.generateKeyPair();
        RSAKey publicJwk = new RSAKey.Builder((RSAPublicKey) keyPair.getPublic())
                .keyID("client-key")
                .algorithm(JWSAlgorithm.RS256)
                .build();
        RegisteredClient client = registeredClient(new JWKSet(publicJwk).toString());
        setAuthorizationServerContext();

        Instant now = Instant.now();
        SignedJWT assertion = new SignedJWT(
                new JWSHeader.Builder(JWSAlgorithm.RS256).keyID("client-key").build(),
                new JWTClaimsSet.Builder()
                        .issuer(client.getClientId())
                        .subject(client.getClientId())
                        .audience(ISSUER + "/oauth2/token")
                        .issueTime(Date.from(now))
                        .expirationTime(Date.from(now.plusSeconds(60)))
                        .jwtID("assertion-1")
                        .build());
        assertion.sign(new RSASSASigner((RSAPrivateKey) keyPair.getPrivate()));

        Jwt decoded = new TakiboJwtClientAssertionDecoderFactory()
                .createDecoder(client)
                .decode(assertion.serialize());

        assertThat(decoded.getSubject()).isEqualTo(client.getClientId());
        assertThat(decoded.getAudience()).contains(ISSUER + "/oauth2/token");
    }

    @Test
    void rejects_malformed_embedded_jwk_set_as_invalid_client() {
        RegisteredClient client = registeredClient("not-json");

        assertThatThrownBy(() -> new TakiboJwtClientAssertionDecoderFactory().createDecoder(client))
                .isInstanceOf(OAuth2AuthenticationException.class)
                .satisfies(ex -> assertThat(((OAuth2AuthenticationException) ex).getError().getErrorCode())
                        .isEqualTo("invalid_client"));
    }

    private static RegisteredClient registeredClient(String jwkSetJson) {
        return RegisteredClient.withId("client-id")
                .clientId("signed-client")
                .authorizationGrantType(AuthorizationGrantType.CLIENT_CREDENTIALS)
                .clientSettings(ClientSettings.builder()
                        .tokenEndpointAuthenticationSigningAlgorithm(SignatureAlgorithm.RS256)
                        .setting(TakiboJwtClientAssertionDecoderFactory.JWK_SET_JSON_SETTING, jwkSetJson)
                        .build())
                .build();
    }

    private static void setAuthorizationServerContext() {
        AuthorizationServerSettings settings = AuthorizationServerSettings.builder().issuer(ISSUER).build();
        AuthorizationServerContextHolder.setContext(new AuthorizationServerContext() {
            @Override
            public String getIssuer() {
                return ISSUER;
            }

            @Override
            public AuthorizationServerSettings getAuthorizationServerSettings() {
                return settings;
            }
        });
    }
}
