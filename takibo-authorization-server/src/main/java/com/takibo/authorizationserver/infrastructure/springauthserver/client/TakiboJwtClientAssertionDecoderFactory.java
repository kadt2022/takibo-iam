package com.takibo.authorizationserver.infrastructure.springauthserver.client;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.proc.SecurityContext;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;
import org.springframework.security.oauth2.jose.jws.JwsAlgorithm;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtDecoderFactory;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.authorization.authentication.JwtClientAssertionDecoderFactory;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.text.ParseException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Adds support for client assertion verification keys stored directly in the client record.
 * Remote JWK Set URLs continue to use Spring Authorization Server's standard decoder factory.
 */
@Component
public final class TakiboJwtClientAssertionDecoderFactory implements JwtDecoderFactory<RegisteredClient> {

    public static final String JWK_SET_JSON_SETTING = "takibo.jwks_json";

    private static final String ERROR_URI =
            "https://datatracker.ietf.org/doc/html/rfc7523#section-3";

    private final JwtClientAssertionDecoderFactory delegate = new JwtClientAssertionDecoderFactory();
    private final Map<String, CachedDecoder> embeddedDecoders = new ConcurrentHashMap<>();

    @Override
    public JwtDecoder createDecoder(RegisteredClient registeredClient) {
        String jwkSetJson = registeredClient.getClientSettings().getSetting(JWK_SET_JSON_SETTING);
        if (!StringUtils.hasText(jwkSetJson)) {
            return delegate.createDecoder(registeredClient);
        }
        if (StringUtils.hasText(registeredClient.getClientSettings().getJwkSetUrl())) {
            throw invalidClient("Both an embedded JWK Set and a JWK Set URL are configured", null);
        }

        JwsAlgorithm configuredAlgorithm = registeredClient.getClientSettings()
                .getTokenEndpointAuthenticationSigningAlgorithm();
        if (!(configuredAlgorithm instanceof SignatureAlgorithm signatureAlgorithm)) {
            throw invalidClient("A supported signature algorithm is required for private_key_jwt", null);
        }

        String fingerprint = signatureAlgorithm.getName() + '\n' + jwkSetJson;
        return embeddedDecoders.compute(registeredClient.getId(), (clientId, cached) -> {
            if (cached != null && cached.fingerprint().equals(fingerprint)) {
                return cached;
            }
            return new CachedDecoder(fingerprint,
                    buildEmbeddedDecoder(registeredClient, jwkSetJson, signatureAlgorithm));
        }).decoder();
    }

    private static JwtDecoder buildEmbeddedDecoder(
            RegisteredClient registeredClient,
            String jwkSetJson,
            SignatureAlgorithm signatureAlgorithm) {
        try {
            JWKSet jwkSet = JWKSet.parse(jwkSetJson);
            if (jwkSet.getKeys().isEmpty()) {
                throw invalidClient("The embedded JWK Set is empty", null);
            }
            NimbusJwtDecoder decoder = NimbusJwtDecoder
                    .withJwkSource(new ImmutableJWKSet<SecurityContext>(jwkSet))
                    .jwsAlgorithm(signatureAlgorithm)
                    .build();
            decoder.setJwtValidator(
                    JwtClientAssertionDecoderFactory.DEFAULT_JWT_VALIDATOR_FACTORY.apply(registeredClient));
            return decoder;
        } catch (ParseException | IllegalArgumentException ex) {
            throw invalidClient("The embedded JWK Set is invalid", ex);
        }
    }

    private static OAuth2AuthenticationException invalidClient(String description, Throwable cause) {
        OAuth2Error error = new OAuth2Error(OAuth2ErrorCodes.INVALID_CLIENT, description, ERROR_URI);
        return cause == null
                ? new OAuth2AuthenticationException(error)
                : new OAuth2AuthenticationException(error, cause);
    }

    private record CachedDecoder(String fingerprint, JwtDecoder decoder) {
    }
}
