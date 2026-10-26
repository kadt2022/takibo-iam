package com.takibo.securitymanagement.infrastructure.security;

import com.takibo.securitycontext.exception.InvalidTakiboSecurityContextException;
import com.takibo.securitycontext.model.*;
import com.takibo.securitycontext.validation.TakiboSecurityContextValidators;

import jakarta.servlet.http.HttpServletRequest;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.UUID;

final class TakiboSecurityContextFactory {

    private static final Pattern REQUEST_ID = Pattern.compile("[A-Za-z0-9._:=+/-]{1,128}");

    private TakiboSecurityContextFactory() {
    }

    /**
     * Construit le contexte à partir de claims déjà vérifiés cryptographiquement. Loi d'identité
     * (IAM-JWT-01) : pour un humain, {@code subjectId} est l'Account ({@code account_id}, égal à
     * {@code sub}) ; le User local reste dans l'attribut {@code USER_ID}. Une incohérence est
     * rejetée sans secours (jamais un identifiant substitué à un autre).
     */
    static TakiboSecurityContext from(Map<String, Object> claims, HttpServletRequest request) {
        return from(claims, request, TrustedProxies.none());
    }

    /**
     * Comme {@link #from(Map, HttpServletRequest)}, l'adresse du client n'étant lue dans
     * X-Forwarded-For que si la connexion vient d'un proxy de confiance.
     */
    static TakiboSecurityContext from(Map<String, Object> claims, HttpServletRequest request,
                                      TrustedProxies trustedProxies) {
        UUID accountId = readUuid(claims, "accountId");
        UUID userId = readUuid(claims, "userId");
        UUID orgIdClaim = readUuid(claims, "orgId");
        UUID spaceIdClaim = readUuid(claims, "spaceId");
        String sub = ClaimReader.readString(claims, "sub");
        String subjectType = ClaimReader.readString(claims, "subjectType");
        String scopeLevel = ClaimReader.readString(claims, "scopeLevel");
        String authMethodClaim = ClaimReader.readString(claims, "authMethod");

        SubjectNature subjectNature;
        AuthenticationMethod authMethod;
        String actorId;

        // On lit la nature du sujet EXPLICITEMENT (subject_type émis par TAS) ;
        // l'inférence par présence d'accountId reste un fallback pour les anciens tokens.
        boolean isHuman = subjectType != null
                ? com.takibo.securitymanagement.domain.model.Subject.TYPE_HUMAN.equals(subjectType)
                : accountId != null;

        if (!isHuman) {
            // Séparation humain/machine : un jeton machine ne porte jamais d'identité humaine.
            // Les consommateurs lisent ACCOUNT_ID/USER_ID sans revérifier la nature du sujet.
            if (accountId != null || userId != null) {
                throw new InvalidTakiboSecurityContextException(
                        "Non-human token must not carry account_id or user_id");
            }
            if (sub == null || sub.isBlank()) {
                throw new InvalidTakiboSecurityContextException("Service token must contain a non-blank subject");
            }
            actorId = sub;
            subjectNature = SubjectNature.SERVICE;
            authMethod = AuthenticationMethod.OAUTH2;
        } else {
            actorId = humanSubjectId(accountId, userId, sub, orgIdClaim, spaceIdClaim);
            subjectNature = SubjectNature.HUMAN;
            authMethod = "PASSWORD".equals(authMethodClaim)
                    ? AuthenticationMethod.PASSWORD
                    : AuthenticationMethod.OIDC;
        }

        Set<String> roles = ClaimReader.readStringSet(claims, "roles");

        SubjectIdentity actor = new SubjectIdentity(
                actorId,
                subjectNature,
                roles,
                authMethod
        );

        String orgId = uuidToString(orgIdClaim);
        String spaceId = uuidToString(spaceIdClaim);

        TenantScope tenant = new TenantScope(orgId, spaceId);

        String requestId = firstValidRequestId(
                request.getHeader("X-Request-Id"),
                request.getHeader("X-Correlation-Id")
        );

        String forwardedFor = request.getHeader("X-Forwarded-For");
        String ip = clientIp(forwardedFor, request.getRemoteAddr(), trustedProxies);
        String protocol = request.getScheme();
        String host = request.getServerName();
        int port = request.getServerPort();

        TransportContext transport = new TransportContext(
                requestId,
                ip,
                forwardedFor,
                protocol,
                null,
                null,
                host,
                port
        );

        String userAgent = request.getHeader("User-Agent");
        ClientChannel channel = ClientChannel.API;

        ClientContext client = new ClientContext(
                userAgent,
                channel,
                null,
                null
        );

        Instant issuedAt = Instant.now();
        Object iat = claims.get("iat");
        if (iat instanceof Number n) {
            issuedAt = Instant.ofEpochSecond(n.longValue());
        }

        TemporalContext temporal = new TemporalContext(
                issuedAt,
                requestId,
                ClaimReader.readString(claims, "iss"),
                1
        );

        Map<AttributeKey, Object> attributes = new LinkedHashMap<>();
        if (accountId != null) {
            attributes.put(StandardAttributeKeys.ACCOUNT_ID, accountId);
        }
        if (userId != null) {
            attributes.put(StandardAttributeKeys.USER_ID, userId);
        }
        if (scopeLevel != null && !scopeLevel.isBlank()) {
            attributes.put(StandardAttributeKeys.SCOPE_LEVEL, scopeLevel);
        }

        return TakiboSecurityContext.builder()
                .subject(actor)
                .tenant(tenant)
                .transport(transport)
                .client(client)
                .temporal(temporal)
                .attributes(new ContextAttributeStore(attributes))
                .build();
    }

    /**
     * Sujet humain : l'Account quand le jeton le porte (profil canonique TAS), avec contrôle de
     * cohérence. Sans {@code account_id} (profil hérité, p. ex. plateforme), le comportement
     * antérieur est conservé : {@code sub}, à défaut {@code userId} ; cet identifiant n'est alors
     * jamais présenté comme un Account.
     */
    private static String humanSubjectId(UUID accountId, UUID userId, String sub,
                                         UUID orgId, UUID spaceId) {
        if (accountId == null) {
            String legacy = firstNonBlank(sub, userId != null ? userId.toString() : null);
            if (legacy == null) {
                throw new InvalidTakiboSecurityContextException("Human token must identify its subject");
            }
            return legacy;
        }
        if (sub == null) {
            throw new InvalidTakiboSecurityContextException("Human token with account_id must carry sub");
        }
        if (!sub.equalsIgnoreCase(accountId.toString())) {
            throw new InvalidTakiboSecurityContextException("Human token sub must equal account_id");
        }
        if (orgId == null) {
            throw new InvalidTakiboSecurityContextException("Human token with account_id must carry org_id");
        }
        if ((userId == null) != (spaceId == null)) {
            throw new InvalidTakiboSecurityContextException(
                    "Human token must carry user_id and space_id together, or neither");
        }
        return accountId.toString();
    }

    private static UUID readUuid(Map<String, Object> claims, String key) {
        try {
            return ClaimReader.readUuid(claims, key);
        } catch (IllegalArgumentException e) {
            throw new InvalidTakiboSecurityContextException("Malformed identity claim: " + key, e);
        }
    }

    private static String uuidToString(UUID uuid) {
        return uuid == null ? null : uuid.toString();
    }

    private static String firstNonBlank(String... values) {
        for (String v : values) {
            if (v != null && !v.isBlank()) return v;
        }
        return null;
    }

    /**
     * Premier identifiant de requête acceptable. Ces en-têtes viennent du client et finissent
     * dans les journaux et l'audit : une valeur trop longue ou portant d'autres caractères
     * (espaces, retours à la ligne, caractères de contrôle) est ignorée plutôt que recopiée.
     */
    private static String firstValidRequestId(String... values) {
        for (String v : values) {
            if (v != null && REQUEST_ID.matcher(v).matches()) return v;
        }
        return null;
    }

    /**
     * IP du client. X-Forwarded-For est fourni par le client : il n'est lu que si la connexion
     * vient d'un proxy de confiance. On remonte alors la chaîne de droite à gauche, chaque proxy
     * de confiance ajoutant l'adresse de son interlocuteur en fin d'en-tête : la première adresse
     * qui n'est pas un proxy de confiance est le client. Les entrées de gauche, que le client peut
     * écrire lui-même, ne sont jamais crues. Une entrée qui n'est pas une IP littérale arrête la
     * remontée, sans résolution DNS ni rejet de la requête.
     */
    static String clientIp(String forwardedFor, String remoteAddr, TrustedProxies trustedProxies) {
        if (forwardedFor == null || forwardedFor.isBlank() || !trustedProxies.contains(remoteAddr)) {
            return remoteAddr;
        }
        String client = remoteAddr;
        String[] hops = forwardedFor.split(",");
        for (int i = hops.length - 1; i >= 0; i--) {
            String hop = hops[i].trim();
            if (!TakiboSecurityContextValidators.isIpLiteral(hop)) {
                return client;
            }
            client = hop;
            if (!trustedProxies.contains(hop)) {
                return hop;
            }
        }
        return client;
    }
}
