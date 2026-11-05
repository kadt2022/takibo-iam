package com.takibo.authorizationserver.infrastructure.springauthserver.client;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.proc.SecurityContext;
import com.takibo.authorizationserver.infrastructure.jwks.DestinationRefusedException;
import com.takibo.authorizationserver.infrastructure.jwks.JwksFetchClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

import java.net.URI;
import java.text.ParseException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * Décodeurs des assertions {@code private_key_jwt} et {@code client_secret_jwt}.
 * <ul>
 *   <li>JWK Set stocké dans le client : vérifié localement, sans réseau.</li>
 *   <li>JWK Set distant ({@code jwksUri}) : récupéré exclusivement par {@link JwksFetchClient}
 *       (SSRF). Le décodeur est construit comme celui de Spring Authorization Server
 *       ({@code NimbusJwtDecoder.withJwkSetUri(...).jwsAlgorithm(...)}) avec ses validateurs
 *       ({@link JwtClientAssertionDecoderFactory#DEFAULT_JWT_VALIDATOR_FACTORY}) ; seul le
 *       client HTTP diffère.</li>
 *   <li>Tout le reste (HMAC, configuration incomplète) : délégué à Spring Authorization Server.</li>
 * </ul>
 * Chaque décodeur est mis en cache avec l'empreinte de sa configuration : une URL ou un
 * algorithme modifié produit un nouveau décodeur.
 */
@Component
public final class TakiboJwtClientAssertionDecoderFactory implements JwtDecoderFactory<RegisteredClient> {

    public static final String JWK_SET_JSON_SETTING = "takibo.jwks_json";

    private static final Logger log = LoggerFactory.getLogger(TakiboJwtClientAssertionDecoderFactory.class);

    private static final String ERROR_URI =
            "https://datatracker.ietf.org/doc/html/rfc7523#section-3";

    private final JwtClientAssertionDecoderFactory delegate = new JwtClientAssertionDecoderFactory();
    private final Map<String, CachedDecoder> decoders = new ConcurrentHashMap<>();
    private final JwksFetchClient jwksFetchClient;

    public TakiboJwtClientAssertionDecoderFactory(JwksFetchClient jwksFetchClient) {
        this.jwksFetchClient = jwksFetchClient;
    }

    @Override
    public JwtDecoder createDecoder(RegisteredClient registeredClient) {
        String jwkSetJson = registeredClient.getClientSettings().getSetting(JWK_SET_JSON_SETTING);
        if (!StringUtils.hasText(jwkSetJson)) {
            return remoteOrDelegatedDecoder(registeredClient);
        }
        if (StringUtils.hasText(registeredClient.getClientSettings().getJwkSetUrl())) {
            throw invalidClient("Both an embedded JWK Set and a JWK Set URL are configured", null);
        }

        JwsAlgorithm configuredAlgorithm = registeredClient.getClientSettings()
                .getTokenEndpointAuthenticationSigningAlgorithm();
        if (!(configuredAlgorithm instanceof SignatureAlgorithm signatureAlgorithm)) {
            throw invalidClient("A supported signature algorithm is required for private_key_jwt", null);
        }

        String fingerprint = "embedded\n" + signatureAlgorithm.getName() + '\n' + jwkSetJson;
        return cached(registeredClient, fingerprint,
                () -> buildEmbeddedDecoder(registeredClient, jwkSetJson, signatureAlgorithm));
    }

    // java:S2139 : le refus est journalisé ici ET relancé à dessein. Spring Authorization Server
    // ne trace l'échec d'authentification du client qu'en debug et ne renvoie qu'une description
    // générique : ce journal est le seul signal d'exploitation (client et destination refusée).
    @SuppressWarnings("java:S2139")
    private JwtDecoder remoteOrDelegatedDecoder(RegisteredClient registeredClient) {
        String jwkSetUrl = registeredClient.getClientSettings().getJwkSetUrl();
        JwsAlgorithm configuredAlgorithm = registeredClient.getClientSettings()
                .getTokenEndpointAuthenticationSigningAlgorithm();
        if (!StringUtils.hasText(jwkSetUrl) || !(configuredAlgorithm instanceof SignatureAlgorithm signatureAlgorithm)) {
            return delegate.createDecoder(registeredClient);
        }
        try {
            jwksFetchClient.checkUri(URI.create(jwkSetUrl));
        } catch (DestinationRefusedException | IllegalArgumentException refused) {
            log.warn("JWKS : destination refusée pour le client {} : {}",
                    registeredClient.getClientId(), refused.getMessage());
            throw invalidClient("The JWK Set URL is not an allowed destination", refused);
        }
        String fingerprint = "remote\n" + signatureAlgorithm.getName() + '\n' + jwkSetUrl;
        return cached(registeredClient, fingerprint, () -> {
            NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSetUri(jwkSetUrl)
                    .jwsAlgorithm(signatureAlgorithm)
                    .restOperations(jwksFetchClient.restOperations())
                    .build();
            decoder.setJwtValidator(
                    JwtClientAssertionDecoderFactory.DEFAULT_JWT_VALIDATOR_FACTORY.apply(registeredClient));
            return decoder;
        });
    }

    private JwtDecoder cached(RegisteredClient registeredClient, String fingerprint, Supplier<JwtDecoder> builder) {
        return decoders.compute(registeredClient.getId(), (clientId, cached) -> {
            if (cached != null && cached.fingerprint().equals(fingerprint)) {
                return cached;
            }
            return new CachedDecoder(fingerprint, builder.get());
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
