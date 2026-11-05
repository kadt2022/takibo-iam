package com.takibo.authorizationserver.infrastructure.springauthserver.client;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.ECDSASigner;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.Curve;
import com.nimbusds.jose.jwk.ECKey;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.ECKeyGenerator;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.PlainJWT;
import com.nimbusds.jwt.SignedJWT;
import com.sun.net.httpserver.HttpsServer;
import com.takibo.authorizationserver.infrastructure.jwks.JwksFetchClient;
import com.takibo.authorizationserver.infrastructure.jwks.OutboundDestinationPolicy;
import com.takibo.authorizationserver.infrastructure.jwks.TestTls;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.context.AuthorizationServerContext;
import org.springframework.security.oauth2.server.authorization.context.AuthorizationServerContextHolder;
import org.springframework.security.oauth2.server.authorization.settings.AuthorizationServerSettings;
import org.springframework.security.oauth2.server.authorization.settings.ClientSettings;

import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.UnaryOperator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Le décodeur des assertions à JWK Set distant conserve chaque contrôle de Spring
 * Authorization Server : {@code iss}, {@code sub}, {@code aud}, {@code exp}, {@code nbf} et
 * l'algorithme du client. Seul le client HTTP a changé ; un JWK Set réel est servi en HTTPS
 * local par un nom de confiance.
 */
class RemoteJwkSetAssertionValidationTest {

    private static final String ISSUER = "https://id.example";
    private static final String AUDIENCE = ISSUER + "/oauth2/token";
    private static final String CLIENT_ID = "remote-signed-client";

    private static RSAKey clientKey;
    private static ECKey otherAlgorithmKey;
    private static HttpsServer jwksServer;
    private static JwksFetchClient fetchClient;

    private TakiboJwtClientAssertionDecoderFactory factory;

    @BeforeAll
    static void serveClientJwkSet() throws Exception {
        clientKey = new RSAKeyGenerator(2048).keyID("rsa-key").generate();
        otherAlgorithmKey = new ECKeyGenerator(Curve.P_256).keyID("ec-key").generate();
        byte[] jwks = new JWKSet(List.of(clientKey.toPublicJWK(), otherAlgorithmKey.toPublicJWK()))
                .toString().getBytes(StandardCharsets.UTF_8);
        TestTls tls = TestTls.selfSigned("dns:localhost");
        jwksServer = tls.server("127.0.0.1", exchange -> {
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, jwks.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(jwks);
            }
        });
        fetchClient = new JwksFetchClient(
                new OutboundDestinationPolicy(List.of("localhost"), List.of(), false),
                new JwksFetchClient.Limits(Duration.ofSeconds(2), Duration.ofSeconds(3), Duration.ofSeconds(5), 50 * 1024),
                tls.context());
    }

    @AfterAll
    static void stopServer() {
        jwksServer.stop(0);
    }

    @BeforeEach
    void setUp() {
        factory = new TakiboJwtClientAssertionDecoderFactory(fetchClient);
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

    @AfterEach
    void clearContext() {
        AuthorizationServerContextHolder.resetContext();
    }

    @Test
    void a_valid_assertion_is_accepted() throws Exception {
        assertThatCode(() -> decoder().decode(rsaSigned(claims -> claims))).doesNotThrowAnyException();
    }

    @Test
    void a_wrong_issuer_is_refused() throws Exception {
        assertRefused(rsaSigned(claims -> claims.issuer("someone-else")));
    }

    @Test
    void a_wrong_subject_is_refused() throws Exception {
        assertRefused(rsaSigned(claims -> claims.subject("someone-else")));
    }

    @Test
    void a_missing_audience_is_refused() throws Exception {
        assertRefused(rsaSigned(claims -> claims.audience((String) null)));
    }

    @Test
    void a_foreign_audience_is_refused() throws Exception {
        assertRefused(rsaSigned(claims -> claims.audience("https://other.example/oauth2/token")));
    }

    @Test
    void a_missing_expiration_is_refused() throws Exception {
        assertRefused(rsaSigned(claims -> claims.expirationTime(null)));
    }

    @Test
    void an_expired_assertion_is_refused() throws Exception {
        Instant past = Instant.now().minusSeconds(600);
        assertRefused(rsaSigned(claims -> claims.issueTime(Date.from(past)).expirationTime(Date.from(past.plusSeconds(60)))));
    }

    @Test
    void an_assertion_not_yet_valid_is_refused() throws Exception {
        assertRefused(rsaSigned(claims -> claims.notBeforeTime(Date.from(Instant.now().plusSeconds(600)))
                .expirationTime(Date.from(Instant.now().plusSeconds(900)))));
    }

    @Test
    void an_algorithm_other_than_the_clients_is_refused_even_with_a_published_key() throws Exception {
        SignedJWT es256 = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.ES256).keyID("ec-key").build(),
                validClaims().build());
        es256.sign(new ECDSASigner(otherAlgorithmKey));

        assertRefused(es256.serialize());
    }

    @Test
    void an_unsigned_assertion_is_refused() {
        assertRefused(new PlainJWT(validClaims().build()).serialize());
    }

    @Test
    void an_hmac_assertion_keyed_with_the_public_key_is_refused() throws Exception {
        SignedJWT hs256 = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.HS256).keyID("rsa-key").build(),
                validClaims().build());
        byte[] publicKeyAsSecret = clientKey.toPublicJWK().toJSONString().getBytes(StandardCharsets.UTF_8);
        hs256.sign(new MACSigner(publicKeyAsSecret));

        assertRefused(hs256.serialize());
    }

    @Test
    void the_cached_decoder_follows_a_changed_jwk_set_url() {
        JwtDecoder first = factory.createDecoder(client("https://localhost:" + jwksServer.getAddress().getPort() + "/jwks"));
        JwtDecoder same = factory.createDecoder(client("https://localhost:" + jwksServer.getAddress().getPort() + "/jwks"));
        JwtDecoder changed = factory.createDecoder(client("https://localhost:" + jwksServer.getAddress().getPort() + "/v2/jwks"));

        assertThat(same).isSameAs(first);
        assertThat(changed).isNotSameAs(first);
    }

    @Test
    void an_internal_jwk_set_url_is_refused_before_any_connection() throws Exception {
        AtomicInteger accepted = new AtomicInteger();
        try (ServerSocket internal = new ServerSocket()) {
            internal.bind(new InetSocketAddress(InetAddress.getByName("127.0.0.1"), 0));
            Thread acceptor = new Thread(() -> {
                while (!internal.isClosed()) {
                    try (Socket ignored = internal.accept()) {
                        accepted.incrementAndGet();
                    } catch (Exception closed) {
                        return;
                    }
                }
            });
            acceptor.setDaemon(true);
            acceptor.start();
            TakiboJwtClientAssertionDecoderFactory defaults =
                    new TakiboJwtClientAssertionDecoderFactory(JwksFetchClients.defaults());

            assertThatThrownBy(() -> defaults.createDecoder(client("https://127.0.0.1:" + internal.getLocalPort() + "/jwks")))
                    .isInstanceOfSatisfying(OAuth2AuthenticationException.class, refused -> {
                        assertThat(refused.getError().getErrorCode()).isEqualTo("invalid_client");
                        assertThat(refused.getError().getDescription()).isEqualTo(
                                "The JWK Set URL is not an allowed destination");
                    });
            assertThat(accepted).hasValue(0);
        }
    }

    // ---------- Outils ----------

    private JwtDecoder decoder() {
        return factory.createDecoder(client("https://localhost:" + jwksServer.getAddress().getPort() + "/jwks"));
    }

    private void assertRefused(String assertion) {
        assertThatThrownBy(() -> decoder().decode(assertion)).isInstanceOf(JwtException.class);
    }

    private static RegisteredClient client(String jwkSetUrl) {
        return RegisteredClient.withId("remote-client-id")
                .clientId(CLIENT_ID)
                .authorizationGrantType(AuthorizationGrantType.CLIENT_CREDENTIALS)
                .clientSettings(ClientSettings.builder()
                        .jwkSetUrl(jwkSetUrl)
                        .tokenEndpointAuthenticationSigningAlgorithm(SignatureAlgorithm.RS256)
                        .build())
                .build();
    }

    private static JWTClaimsSet.Builder validClaims() {
        Instant now = Instant.now();
        return new JWTClaimsSet.Builder()
                .issuer(CLIENT_ID)
                .subject(CLIENT_ID)
                .audience(AUDIENCE)
                .issueTime(Date.from(now))
                .expirationTime(Date.from(now.plusSeconds(60)))
                .jwtID(UUID.randomUUID().toString());
    }

    private static String rsaSigned(UnaryOperator<JWTClaimsSet.Builder> change) throws Exception {
        SignedJWT jwt = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.RS256).keyID("rsa-key").build(),
                change.apply(validClaims()).build());
        jwt.sign(new RSASSASigner(clientKey));
        return jwt.serialize();
    }
}
