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
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
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

        TakiboJwtClientAssertionDecoderFactory factory = new TakiboJwtClientAssertionDecoderFactory(JwksFetchClients.defaults());
        var decoder = factory.createDecoder(client);
        Jwt decoded = decoder.decode(assertion.serialize());

        assertThat(decoded.getSubject()).isEqualTo(client.getClientId());
        assertThat(decoded.getAudience()).contains(ISSUER + "/oauth2/token");
        assertThat(factory.createDecoder(client)).isSameAs(decoder);
    }

    /**
     * Un JWK intégré enregistré avant la règle « RSA ≥ 2048 bits » reste en base : la
     * vérification doit l'écarter au moment de l'authentification, pas seulement à
     * l'enregistrement.
     */
    @Test
    void an_embedded_rsa_key_shorter_than_2048_bits_never_verifies_an_assertion() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(1024);
        KeyPair weak = generator.generateKeyPair();
        RSAKey weakPublicJwk = new RSAKey.Builder((RSAPublicKey) weak.getPublic()).keyID("weak-key").build();
        RegisteredClient client = registeredClient(new JWKSet(weakPublicJwk).toString());
        setAuthorizationServerContext();
        String assertion = signedAssertion(client, "weak-key", new RSASSASigner((RSAPrivateKey) weak.getPrivate(), true));
        JwtDecoder decoder = new TakiboJwtClientAssertionDecoderFactory(JwksFetchClients.defaults()).createDecoder(client);

        assertThatThrownBy(() -> decoder.decode(assertion))
                .as("une clé RSA de 1024 bits ne doit authentifier aucun client")
                .isInstanceOf(JwtException.class);
    }

    @Test
    void rejects_malformed_embedded_jwk_set_as_invalid_client() {
        RegisteredClient client = registeredClient("not-json");
        TakiboJwtClientAssertionDecoderFactory factory = new TakiboJwtClientAssertionDecoderFactory(JwksFetchClients.defaults());

        assertThatThrownBy(() -> factory.createDecoder(client))
                .isInstanceOf(OAuth2AuthenticationException.class)
                .satisfies(ex -> assertThat(((OAuth2AuthenticationException) ex).getError().getErrorCode())
                        .isEqualTo("invalid_client"));
    }

    @Test
    void builds_remote_jwk_set_decoder_through_the_jwks_fetch_client() {
        RegisteredClient client = RegisteredClient.withId("remote-client-id")
                .clientId("remote-client")
                .authorizationGrantType(AuthorizationGrantType.CLIENT_CREDENTIALS)
                .clientSettings(ClientSettings.builder()
                        .jwkSetUrl("https://keys.example/jwks.json")
                        .tokenEndpointAuthenticationSigningAlgorithm(SignatureAlgorithm.RS256)
                        .build())
                .build();

        assertThat(new TakiboJwtClientAssertionDecoderFactory(JwksFetchClients.defaults()).createDecoder(client)).isNotNull();
    }

    @Test
    void rejects_ambiguous_or_incomplete_embedded_key_settings() {
        RegisteredClient bothSources = RegisteredClient.withId("both-sources")
                .clientId("both-sources")
                .authorizationGrantType(AuthorizationGrantType.CLIENT_CREDENTIALS)
                .clientSettings(ClientSettings.builder()
                        .jwkSetUrl("https://keys.example/jwks.json")
                        .tokenEndpointAuthenticationSigningAlgorithm(SignatureAlgorithm.RS256)
                        .setting(TakiboJwtClientAssertionDecoderFactory.JWK_SET_JSON_SETTING, "{\"keys\":[]}")
                        .build())
                .build();
        RegisteredClient missingAlgorithm = RegisteredClient.withId("missing-algorithm")
                .clientId("missing-algorithm")
                .authorizationGrantType(AuthorizationGrantType.CLIENT_CREDENTIALS)
                .clientSettings(ClientSettings.builder()
                        .setting(TakiboJwtClientAssertionDecoderFactory.JWK_SET_JSON_SETTING, "{\"keys\":[]}")
                        .build())
                .build();
        RegisteredClient emptyKeySet = registeredClient("{\"keys\":[]}");
        TakiboJwtClientAssertionDecoderFactory factory = new TakiboJwtClientAssertionDecoderFactory(JwksFetchClients.defaults());

        assertThatThrownBy(() -> factory.createDecoder(bothSources))
                .isInstanceOf(OAuth2AuthenticationException.class)
                .hasMessageContaining("Both an embedded JWK Set");
        assertThatThrownBy(() -> factory.createDecoder(missingAlgorithm))
                .isInstanceOf(OAuth2AuthenticationException.class)
                .hasMessageContaining("signature algorithm is required");
        assertThatThrownBy(() -> factory.createDecoder(emptyKeySet))
                .isInstanceOf(OAuth2AuthenticationException.class)
                .hasMessageContaining("JWK Set is empty");
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

    private static String signedAssertion(RegisteredClient client, String keyId, RSASSASigner signer) throws Exception {
        Instant now = Instant.now();
        SignedJWT assertion = new SignedJWT(
                new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(keyId).build(),
                new JWTClaimsSet.Builder()
                        .issuer(client.getClientId())
                        .subject(client.getClientId())
                        .audience(ISSUER + "/oauth2/token")
                        .issueTime(Date.from(now))
                        .expirationTime(Date.from(now.plusSeconds(60)))
                        .jwtID("assertion-" + keyId)
                        .build());
        assertion.sign(signer);
        return assertion.serialize();
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
