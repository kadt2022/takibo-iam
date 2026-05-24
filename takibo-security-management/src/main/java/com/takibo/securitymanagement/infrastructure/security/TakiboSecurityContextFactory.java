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

    static TakiboSecurityContext from(Map<String, Object> claims, HttpServletRequest request) {
        UUID accountId = ClaimReader.readUuid(claims, "accountId");
        UUID userId = ClaimReader.readUuid(claims, "userId");
        String sub = ClaimReader.readString(claims, "sub");

        SubjectNature subjectNature;
        AuthenticationMethod authMethod;
        String actorId;

        if (accountId == null) {
            if (sub == null || sub.isBlank()) {
                throw new InvalidTakiboSecurityContextException("Service token must contain a non-blank subject");
            }
            actorId = sub;
            subjectNature = SubjectNature.SERVICE;
            authMethod = AuthenticationMethod.OAUTH2;
        } else {
            actorId = firstNonBlank(
                    userId != null ? userId.toString() : null,
                    sub,
                    accountId.toString()
            );
            subjectNature = SubjectNature.HUMAN;
            authMethod = AuthenticationMethod.OIDC;
        }

        Set<String> roles = ClaimReader.readStringSet(claims, "roles");

        SubjectIdentity actor = new SubjectIdentity(
                actorId,
                subjectNature,
                roles,
                authMethod
        );

        String orgId = uuidToString(ClaimReader.readUuid(claims, "orgId"));
        String spaceId = uuidToString(ClaimReader.readUuid(claims, "spaceId"));

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

        return TakiboSecurityContext.builder()
                .subject(actor)
                .tenant(tenant)
                .transport(transport)
                .client(client)
                .temporal(temporal)
                .attributes(new ContextAttributeStore(attributes))
                .build();
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
