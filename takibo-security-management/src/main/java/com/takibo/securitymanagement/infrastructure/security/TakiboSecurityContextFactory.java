package com.takibo.securitymanagement.infrastructure.security;

import com.takibo.securitycontext.exception.InvalidTakiboSecurityContextException;
import com.takibo.securitycontext.model.*;

import jakarta.servlet.http.HttpServletRequest;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

final class TakiboSecurityContextFactory {

    private TakiboSecurityContextFactory() {
    }

    /**
     * Construit le contexte à partir de claims déjà vérifiés cryptographiquement. Loi d'identité
     * (IAM-JWT-01) : pour un humain, {@code subjectId} est l'Account ({@code account_id}, égal à
     * {@code sub}) ; le User local reste dans l'attribut {@code USER_ID}. Une incohérence est
     * rejetée sans secours (jamais un identifiant substitué à un autre).
     */
    static TakiboSecurityContext from(Map<String, Object> claims, HttpServletRequest request) {
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

        String requestId = firstNonBlank(
                request.getHeader("X-Request-Id"),
                request.getHeader("X-Correlation-Id")
        );

        String forwardedFor = request.getHeader("X-Forwarded-For");
        String ip = firstNonBlank(extractClientIp(forwardedFor), request.getRemoteAddr());
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
        if (sub != null && !sub.equalsIgnoreCase(accountId.toString())) {
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

    private static String extractClientIp(String forwardedFor) {
        if (forwardedFor == null || forwardedFor.isBlank()) return null;
        String[] parts = forwardedFor.split(",");
        if (parts.length == 0) return null;
        String ip = parts[0].trim();
        return ip.isEmpty() ? null : ip;
    }
}
