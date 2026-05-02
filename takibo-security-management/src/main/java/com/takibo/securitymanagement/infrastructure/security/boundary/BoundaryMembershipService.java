package com.takibo.securitymanagement.infrastructure.security.boundary;

import com.takibo.securitycontext.model.TakiboSecurityContext;
import com.takibo.securitycontext.spi.TakiboSecurityContextCarrier;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
@RequiredArgsConstructor
public class BoundaryMembershipService {

    private final JdbcTemplate jdbc;

    private final ConcurrentHashMap<UUID, CacheEntry> orgBySpaceCache = new ConcurrentHashMap<>();
    private final Duration ttl = Duration.ofSeconds(10);

    @Transactional(readOnly = true)
    public void assertActorInSpaceOrg(UUID spaceId, Authentication auth) {
        if (spaceId == null) throw new IllegalArgumentException("SPACE_ID_REQUIRED");
        if (auth == null || !auth.isAuthenticated()) throw new AccessDeniedException("UNAUTHENTICATED");
        if (isPlatformAdmin(auth)) return;

        UUID targetOrg = resolveOrgIdBySpace(spaceId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "SPACE_NOT_FOUND: spaceId=" + spaceId));

        UUID tokenOrg = extractOrgId(auth);

        if (hasAuthority(auth, "ROLE_ORG_ADMIN") || hasAuthority(auth, "ORG_ADMIN")) {
            if (tokenOrg != null && tokenOrg.equals(targetOrg)) {
                log.debug("ORG_ADMIN bypass: token org matches target org ({})", targetOrg);
                return;
            }
        }

        ActorIds ids = extractActorIds(auth)
                .orElseThrow(() -> new IllegalStateException("ACTOR_IDENTITY_MISSING"));

        log.debug("Boundary check: spaceId={}, targetOrg={}, actorIds={}", spaceId, targetOrg, ids);

        boolean ok = false;
        if (ids.userId != null)           ok = existsByUserIdInOrg(ids.userId, targetOrg);
        if (!ok && ids.accountId != null) ok = existsByAccountIdInOrg(ids.accountId, targetOrg);
        if (!ok && ids.username != null)  ok = existsByUsernameInOrg(ids.username, targetOrg);

        if (!ok) {
            log.warn("Boundary violation: spaceId={}, targetOrg={}, actorIds={}", spaceId, targetOrg, ids);
            throw new AccessDeniedException("ACTOR_NOT_IN_SPACE_ORG");
        }

        log.debug("Boundary check PASSED for spaceId={}, targetOrg={}", spaceId, targetOrg);
    }

    private Optional<UUID> resolveOrgIdBySpace(UUID spaceId) {
        CacheEntry hit = orgBySpaceCache.get(spaceId);
        if (hit != null && !hit.isExpired(ttl)) {
            log.debug("Cache HIT for spaceId={} -> orgId={}", spaceId, hit.value);
            return Optional.of(hit.value);
        }

        log.debug("Cache MISS for spaceId={}, querying DB...", spaceId);

        try {
            UUID orgId = jdbc.queryForObject(
                    "SELECT org_id FROM spaces WHERE id = ?",
                    (rs, rowNum) -> readUuid(rs, 1),
                    spaceId
            );
            if (orgId == null) {
                return Optional.empty();
            }
            orgBySpaceCache.put(spaceId, new CacheEntry(orgId, Instant.now()));
            log.debug("Resolved orgId={} for spaceId={}", orgId, spaceId);
            return Optional.of(orgId);
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        } catch (Exception e) {
            log.warn("Failed to resolve orgId for spaceId={}", spaceId, e);
            return Optional.empty();
        }
    }

    private boolean existsByUserIdInOrg(UUID userId, UUID orgId) {
        try {
            Integer c = jdbc.queryForObject(
                    """
                    SELECT COUNT(1)
                    FROM users u
                    JOIN spaces s ON s.id = u.space_id
                    WHERE u.id = ? AND s.org_id = ?
                    """,
                    Integer.class,
                    userId,
                    orgId
            );
            return c != null && c > 0;
        } catch (Exception e) {
            log.error("existsByUserIdInOrg failed for userId={}, orgId={}", userId, orgId, e);
            return false;
        }
    }

    private boolean existsByAccountIdInOrg(UUID accountId, UUID orgId) {
        try {
            Integer c = jdbc.queryForObject(
                    """
                    SELECT COUNT(1)
                    FROM users u
                    JOIN spaces s ON s.id = u.space_id
                    WHERE u.account_id = ? AND s.org_id = ?
                    """,
                    Integer.class,
                    accountId,
                    orgId
            );
            return c != null && c > 0;
        } catch (Exception e) {
            log.error("existsByAccountIdInOrg failed for accountId={}, orgId={}", accountId, orgId, e);
            return false;
        }
    }

    private boolean existsByUsernameInOrg(String username, UUID orgId) {
        try {
            Integer c = jdbc.queryForObject(
                    """
                    SELECT COUNT(1)
                    FROM users u
                    JOIN spaces s ON s.id = u.space_id
                    WHERE u.username = ? AND s.org_id = ?
                    """,
                    Integer.class,
                    username,
                    orgId
            );
            boolean exists = c != null && c > 0;
            log.debug("existsByUsernameInOrg: username={}, orgId={} -> {}", username, orgId, exists);
            return exists;
        } catch (Exception e) {
            log.error("existsByUsernameInOrg failed for username={}, orgId={}", username, orgId, e);
            return false;
        }
    }

    private record ActorIds(UUID userId, UUID accountId, String username) {}

    private Optional<ActorIds> extractActorIds(Authentication auth) {
        if (auth == null || !auth.isAuthenticated()) return Optional.empty();

        // 1) Token custom Takibo (nouveau)
        if (auth instanceof TakiboSecurityContextCarrier carrier) {
            TakiboSecurityContext ctx = carrier.getSecurityContext();

            UUID accountId = extractAccountIdFromActorIdOrNull(ctx.subject().subjectId()); // fallback si tu encodes accountId en subjectId
            UUID userId = extractUserIdFromActorIdOrNull(ctx.subject().subjectId());

            // IMPORTANT:
            // Notre contrat core ne met pas "username" en V1.
            // Donc on prend le principalName (string) si disponible.
            String username = null;
            Object p = auth.getPrincipal();
            if (p instanceof String s && !s.isBlank()) {
                username = s;
            }

            // Si subjectId est un UUID d'user (cas courant), userId = subjectId.
            // accountId n'est pas dans V1 core -> on garde ce fallback minimal.
            if (userId == null) {
                userId = tryParseUuid(ctx.subject().subjectId());
            }

            log.debug("Extracted from TakiboSecurityContextCarrier: subjectId={}, userId={}, username={}",
                    ctx.subject().subjectId(), userId, username);

            return Optional.of(new ActorIds(userId, accountId, username));
        }

        // 2) JwtAuthenticationToken Spring resource server
        if (auth instanceof org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken jat) {
            Map<String, Object> m = jat.getTokenAttributes();
            return Optional.of(new ActorIds(
                    parseUuid(m.get("userId"), m.get("sub")),
                    parseUuid(m.get("accountId")),
                    parseString(m.get("preferred_username"), m.get("username"), m.get("upn"), m.get("sub"))
            ));
        }

        // 3) Jwt direct
        Object p = auth.getPrincipal();
        if (p instanceof org.springframework.security.oauth2.jwt.Jwt jwt) {
            Map<String, Object> m = jwt.getClaims();
            return Optional.of(new ActorIds(
                    parseUuid(m.get("userId"), m.get("sub")),
                    parseUuid(m.get("accountId")),
                    parseString(m.get("preferred_username"), m.get("username"), m.get("upn"), m.get("sub"))
            ));
        }

        log.warn("Could not extract actor identity from authentication: {}", auth.getClass().getName());
        return Optional.empty();
    }

    private UUID extractOrgId(Authentication auth) {
        if (auth instanceof TakiboSecurityContextCarrier carrier) {
            TakiboSecurityContext ctx = carrier.getSecurityContext();
            return tryParseUuid(ctx.tenant().organizationId());
        }
        return null;
    }

    private boolean hasAuthority(Authentication auth, String expected) {
        for (GrantedAuthority ga : auth.getAuthorities()) {
            if (expected.equals(ga.getAuthority())) return true;
        }
        return false;
    }

    private UUID parseUuid(Object... vals) {
        for (Object v : vals) {
            if (v instanceof UUID u) return u;
            if (v instanceof String s && !s.isBlank()) {
                try { return UUID.fromString(s); } catch (Exception ignored) {}
            }
        }
        return null;
    }

    private String parseString(Object... vals) {
        for (Object v : vals) if (v instanceof String s && !s.isBlank()) return s;
        return null;
    }

    private UUID tryParseUuid(String s) {
        if (s == null || s.isBlank()) return null;
        try {
            return UUID.fromString(s.trim());
        } catch (Exception e) {
            return null;
        }
    }

    private UUID extractUserIdFromActorIdOrNull(String actorId) {
        // V1: on suppose que subjectId peut être userId (UUID). Si ce n’est pas le cas => null.
        return tryParseUuid(actorId);
    }

    private UUID extractAccountIdFromActorIdOrNull(String actorId) {
        // V1: rien dans le contrat pour accountId.
        // Si tu veux, on fera V1.2 avec ActorIdentity enrichi (accountId séparé).
        return null;
    }

    private boolean isPlatformAdmin(Authentication auth) {
        for (GrantedAuthority ga : auth.getAuthorities()) {
            String a = ga.getAuthority();
            if ("PLATFORM_ADMIN".equals(a) || "ROLE_PLATFORM_ADMIN".equals(a)) return true;
        }
        return false;
    }

    private record CacheEntry(UUID value, Instant at) {
        boolean isExpired(Duration ttl) { return Instant.now().isAfter(at.plus(ttl)); }
    }

    private UUID readUuid(ResultSet rs, int index) throws SQLException {
        Object raw = rs.getObject(index);
        if (raw == null) return null;

        if (raw instanceof UUID u) {
            return u;
        }
        if (raw instanceof String s) {
            return UUID.fromString(s);
        }
        if (raw instanceof byte[] bytes) {
            if (bytes.length != 16) {
                throw new IllegalStateException("Invalid UUID binary length: " + bytes.length);
            }
            long msb = 0;
            long lsb = 0;
            for (int i = 0; i < 8; i++) {
                msb = (msb << 8) | (bytes[i] & 0xffL);
            }
            for (int i = 8; i < 16; i++) {
                lsb = (lsb << 8) | (bytes[i] & 0xffL);
            }
            return new UUID(msb, lsb);
        }

        throw new IllegalStateException("Cannot convert column " + index + " to UUID, type=" + raw.getClass());
    }
}
