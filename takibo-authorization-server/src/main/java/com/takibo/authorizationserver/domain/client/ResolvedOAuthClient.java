package com.takibo.authorizationserver.domain.client;

import java.time.Duration;
import java.util.Set;
import java.util.UUID;

/**
 * Un client OAuth2 resolu : son identite, son plan, sa frontiere et sa politique.
 * <p>
 * Resultat unique de {@link ResolvedOAuthClientResolver}, partage par les trois chemins qui
 * interrogeaient jusqu'ici le registre chacun de leur cote — le
 * {@code RegisteredClientRepository}, le filtre de resolution de tenant et la politique PKCE.
 * Une seule lecture, une seule verite : deux chemins ne peuvent plus diverger sur le meme
 * {@code client_id}.
 * <p>
 * Le TAS ne fabrique aucune frontiere. Ce qui est absent ici est absent partout en aval :
 * un client PLATFORM produit un token sans tenant, et c'est ainsi qu'il se ferme aux routes
 * situees. Les invariants de plan sont verifies a la construction, et un plan incoherent est
 * une erreur de configuration, jamais une valeur par defaut.
 * <p>
 * {@code registeredClientId} est l'identifiant <b>technique</b> et stable du client, celui
 * que Spring Authorization Server persiste. Il ne doit jamais etre confondu avec le
 * {@code clientId} public, globalement unique mais destine a l'exterieur.
 *
 * @param accessTokenTtl  {@code null} laisse le defaut de Spring Authorization Server
 * @param refreshTokenTtl {@code null} laisse le defaut de Spring Authorization Server
 * @param idTokenTtl      {@code null} laisse le defaut de Spring Authorization Server
 */
public record ResolvedOAuthClient(
        String registeredClientId,
        String clientId,
        ClientPlan plan,
        UUID orgId,
        UUID spaceId,
        ClientType clientType,
        boolean requireProofKey,
        boolean requireConsent,
        boolean requireClientSecret,
        String clientSecretHash,
        String tokenEndpointAuthMethod,
        String jwksUri,
        String jwksJson,
        String idTokenSignedAlg,
        Duration accessTokenTtl,
        Duration refreshTokenTtl,
        Duration idTokenTtl,
        Set<String> scopes,
        Set<String> grantTypes,
        Set<String> redirectUris,
        Set<String> postLogoutRedirectUris
) {

    public ResolvedOAuthClient {
        requireText(registeredClientId, "RESOLVED_CLIENT_REQUIRES_TECHNICAL_ID");
        requireText(clientId, "RESOLVED_CLIENT_REQUIRES_CLIENT_ID");
        if (plan == null) {
            throw new IllegalArgumentException("RESOLVED_CLIENT_REQUIRES_PLAN: " + clientId);
        }
        if (clientType == null) {
            throw new IllegalArgumentException("RESOLVED_CLIENT_REQUIRES_TYPE: " + clientId);
        }

        // La frontiere suit le plan, sans exception ni valeur comblee.
        if (plan.requiresOrganization() == (orgId == null)) {
            throw new IllegalArgumentException(
                    (plan.requiresOrganization()
                            ? "CLIENT_PLAN_REQUIRES_ORGANIZATION: "
                            : "PLATFORM_CLIENT_MUST_NOT_CARRY_ORGANIZATION: ") + clientId);
        }
        if (plan.requiresSpace() == (spaceId == null)) {
            throw new IllegalArgumentException(
                    (plan.requiresSpace()
                            ? "CLIENT_PLAN_REQUIRES_SPACE: "
                            : "CLIENT_PLAN_MUST_NOT_CARRY_SPACE: ") + clientId);
        }

        // Un client sans grant type est inutilisable : le refuser ici evite une erreur
        // opaque plus loin, au moment ou Spring Authorization Server tente de le batir.
        grantTypes = immutable(grantTypes);
        if (grantTypes.isEmpty()) {
            throw new IllegalArgumentException("CLIENT_REQUIRES_AT_LEAST_ONE_GRANT_TYPE: " + clientId);
        }
        requireText(tokenEndpointAuthMethod, "CLIENT_REQUIRES_TOKEN_ENDPOINT_AUTH_METHOD");

        if (requireClientSecret && !hasText(clientSecretHash)) {
            throw new IllegalArgumentException("CLIENT_REQUIRES_SECRET_HASH: " + clientId);
        }
        if (clientType == ClientType.PUBLIC && requireClientSecret) {
            throw new IllegalArgumentException("PUBLIC_CLIENT_MUST_NOT_REQUIRE_SECRET: " + clientId);
        }

        requireNonNegative(accessTokenTtl, "ACCESS_TOKEN_TTL_MUST_BE_POSITIVE", clientId);
        requireNonNegative(refreshTokenTtl, "REFRESH_TOKEN_TTL_MUST_BE_POSITIVE", clientId);
        requireNonNegative(idTokenTtl, "ID_TOKEN_TTL_MUST_BE_POSITIVE", clientId);

        scopes = immutable(scopes);
        redirectUris = immutable(redirectUris);
        postLogoutRedirectUris = immutable(postLogoutRedirectUris);
    }

    /**
     * PKCE exige, soit par configuration explicite du client, soit parce qu'un client public
     * ne peut prouver son identite autrement. La regle vit ici plutot que dans le filtre :
     * elle decoule du client, pas de la requete.
     */
    public boolean pkceRequired() {
        return requireProofKey || clientType == ClientType.PUBLIC;
    }

    public boolean supportsGrantType(String grantType) {
        return grantTypes.contains(grantType);
    }

    private static Set<String> immutable(Set<String> values) {
        return values == null ? Set.of() : Set.copyOf(values);
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private static void requireText(String value, String code) {
        if (!hasText(value)) {
            throw new IllegalArgumentException(code);
        }
    }

    private static void requireNonNegative(Duration ttl, String code, String clientId) {
        if (ttl != null && (ttl.isZero() || ttl.isNegative())) {
            throw new IllegalArgumentException(code + ": " + clientId);
        }
    }
}
