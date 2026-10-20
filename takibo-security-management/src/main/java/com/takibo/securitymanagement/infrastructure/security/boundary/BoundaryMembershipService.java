package com.takibo.securitymanagement.infrastructure.security.boundary;

import com.takibo.identitycore.domain.catalogrbac.TechnicalRole;
import com.takibo.securitycontext.model.StandardAttributeKeys;
import com.takibo.securitycontext.model.TakiboSecurityContext;
import com.takibo.securitycontext.spi.TakiboSecurityContextCarrier;
import lombok.extern.slf4j.Slf4j;
import com.takibo.identitycore.domain.exception.SpaceNotFoundException;
import com.takibo.securitymanagement.domain.exception.BoundaryCheckFailedException;
import com.takibo.securitymanagement.domain.exception.BoundaryCheckUnavailableException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Contrôle d'appartenance à l'organisation du Space cible, via un User dans un Space de cette
 * organisation. Il ne prouve ni l'appartenance au Space ni la permission : réussir ce contrôle
 * ne vaut jamais autorisation finale ({@code SpaceBoundaryGuard} et les permissions restent
 * exigés).
 * <p>
 * Contrat d'erreur : non-appartenance prouvée -> {@link AccessDeniedException} (403) ; Space
 * absent -> {@link SpaceNotFoundException} (404) ; base indisponible ->
 * {@link BoundaryCheckUnavailableException} (503) ; erreur structurelle ->
 * {@link BoundaryCheckFailedException} (500). Aucune erreur technique n'est convertie en refus,
 * en absence ni en réussite, et aucun secours (username, autre identifiant) ne la contourne.
 */
@Slf4j
@Service
public class BoundaryMembershipService {

    private static final String ROLE_PREFIX = "ROLE_";

    private final JdbcTemplate jdbc;
    private final Clock clock;

    private final ConcurrentHashMap<UUID, CacheEntry> orgBySpaceCache = new ConcurrentHashMap<>();
    private final Duration ttl = Duration.ofSeconds(10);

    @Autowired
    public BoundaryMembershipService(JdbcTemplate jdbc) {
        this(jdbc, Clock.systemUTC());
    }

    BoundaryMembershipService(JdbcTemplate jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public void assertActorInSpaceOrg(UUID spaceId, Authentication auth) {
        if (spaceId == null) throw new IllegalArgumentException("SPACE_ID_REQUIRED");
        if (auth == null || !auth.isAuthenticated()) {
            throw new AuthenticationCredentialsNotFoundException("UNAUTHENTICATED");
        }
        if (isPlatformAdmin(auth)) return;

        UUID targetOrg = resolveOrgIdBySpace(spaceId);

        UUID tokenOrg = extractOrgId(auth);

        // Bypass org-level : uniquement la vérification d'appartenance à l'ORG.
        // Ne bypass jamais la règle stricte token.space_id d'un token SPACE.
        if (hasAnyAuthority(auth,
                TechnicalRole.ORG_ADMIN.code(), ROLE_PREFIX + TechnicalRole.ORG_ADMIN.code(),
                TechnicalRole.ORG_OWNER.code(), ROLE_PREFIX + TechnicalRole.ORG_OWNER.code())) {
            if (tokenOrg != null && tokenOrg.equals(targetOrg)) {
                log.debug("Org-level role bypass: token org matches target org ({})", targetOrg);
                return;
            }
        }

        ActorIds ids = extractActorIds(auth)
                .orElseThrow(() -> new AccessDeniedException("ACTOR_IDENTITY_MISSING"));

        log.debug("Boundary check: spaceId={}, targetOrg={}, actorIds={}", spaceId, targetOrg, ids);

        boolean ok;
        if (ids.userId != null) {
            // Le User doit appartenir à l'Account du jeton (s'il est porté) et au Space porté
            // par le jeton (pas au Space cible : l'accès à celui-ci relève de SpaceBoundaryGuard).
            // Une relation incohérente ne trouve aucune ligne et est refusée, sans autre essai.
            ok = existsUserInOrg(ids.userId, ids.accountId, extractSpaceId(auth), targetOrg);
        } else if (ids.accountId != null) {
            ok = existsByAccountIdInOrg(ids.accountId, targetOrg);
        } else {
            throw new AccessDeniedException("ACTOR_IDENTITY_MISSING");
        }

        if (!ok) {
            log.warn("Boundary violation: spaceId={}, targetOrg={}, actorIds={}", spaceId, targetOrg, ids);
            throw new AccessDeniedException("ACTOR_NOT_IN_SPACE_ORG");
        }

        log.debug("Boundary check PASSED for spaceId={}, targetOrg={}", spaceId, targetOrg);
    }

    /**
     * Seules les résolutions réussies sont mises en cache (TTL court, sans cache négatif). Une
     * entrée valide peut donc répondre pendant ce TTL même si la base est tombée : la fraîcheur
     * est bornée par le TTL, sans promesse de révocation immédiate.
     */
    private UUID resolveOrgIdBySpace(UUID spaceId) {
        CacheEntry hit = orgBySpaceCache.get(spaceId);
        if (hit != null && !hit.isExpired(ttl, clock)) {
            log.debug("Cache HIT for spaceId={} -> orgId={}", spaceId, hit.value);
            return hit.value;
        }

        log.debug("Cache MISS for spaceId={}, querying DB...", spaceId);

        UUID orgId;
        try {
            orgId = jdbc.queryForObject(
                    "SELECT org_id FROM spaces WHERE id = ?",
                    (rs, rowNum) -> readUuid(rs, 1),
                    spaceId
            );
        } catch (EmptyResultDataAccessException e) {
            throw new SpaceNotFoundException(spaceId);
        } catch (RuntimeException e) {
            throw technicalFailure("resolve org of spaceId=" + spaceId, e);
        }
        if (orgId == null) {
            // Space présent mais sans organisation : donnée incohérente, pas un Space absent.
            throw new BoundaryCheckFailedException(
                    "Space " + spaceId + " has no organization", null);
        }
        orgBySpaceCache.put(spaceId, new CacheEntry(orgId, clock.instant()));
        log.debug("Resolved orgId={} for spaceId={}", orgId, spaceId);
        return orgId;
    }

    private boolean existsUserInOrg(UUID userId, UUID accountId, UUID tokenSpaceId, UUID orgId) {
        StringBuilder sql = new StringBuilder("""
                SELECT COUNT(1)
                FROM users u
                JOIN spaces s ON s.id = u.space_id
                WHERE u.id = ?""");
        List<Object> args = new ArrayList<>();
        args.add(userId);
        if (accountId != null) {
            sql.append(" AND u.account_id = ?");
            args.add(accountId);
        }
        if (tokenSpaceId != null) {
            sql.append(" AND u.space_id = ?");
            args.add(tokenSpaceId);
        }
        sql.append(" AND s.org_id = ?");
        args.add(orgId);
        try {
            Integer c = jdbc.queryForObject(sql.toString(), Integer.class, args.toArray());
            return c != null && c > 0;
        } catch (RuntimeException e) {
            throw technicalFailure("check membership of userId=" + userId + " in orgId=" + orgId, e);
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
        } catch (RuntimeException e) {
            throw technicalFailure("check membership of accountId=" + accountId + " in orgId=" + orgId, e);
        }
    }

    /** Classe une erreur technique : indisponibilité temporaire (503) ou erreur structurelle (500). */
    private RuntimeException technicalFailure(String operation, RuntimeException e) {
        log.error("Boundary check could not {}", operation, e);
        if (e instanceof TransientDataAccessException || e instanceof DataAccessResourceFailureException) {
            return new BoundaryCheckUnavailableException("Boundary store unavailable: " + operation, e);
        }
        return new BoundaryCheckFailedException("Boundary check failed: " + operation, e);
    }

    private record ActorIds(UUID userId, UUID accountId) {}

    /**
     * Seule forme d'Authentication supportée : le carrier TAKIBO (contrat d'identité IAM-JWT-01).
     * Les identifiants sont lus depuis leurs attributs explicites ; les autres formes n'ont pas de
     * producteur dans l'exécution et sont refusées plutôt que devinées.
     */
    private Optional<ActorIds> extractActorIds(Authentication auth) {
        if (auth instanceof TakiboSecurityContextCarrier carrier) {
            TakiboSecurityContext ctx = carrier.getSecurityContext();

            // Loi d'identité (IAM-JWT-01) : subjectId est l'Account pour un humain, jamais un User.
            // Account et User sont lus depuis leurs attributs explicites ; rien n'est déduit du
            // subjectId (un UUID ne révèle pas son type, p. ex. le sub d'un client machine).
            UUID accountId = ctx.attributes().get(StandardAttributeKeys.ACCOUNT_ID, UUID.class).orElse(null);
            UUID userId = ctx.attributes().get(StandardAttributeKeys.USER_ID, UUID.class).orElse(null);

            log.debug("Extracted from TakiboSecurityContextCarrier: accountId={}, userId={}", accountId, userId);

            return Optional.of(new ActorIds(userId, accountId));
        }

        log.warn("Unsupported authentication form for boundary check: {}", auth.getClass().getName());
        return Optional.empty();
    }

    private UUID extractOrgId(Authentication auth) {
        if (auth instanceof TakiboSecurityContextCarrier carrier) {
            TakiboSecurityContext ctx = carrier.getSecurityContext();
            return tryParseUuid(ctx.tenant().organizationId());
        }
        return null;
    }

    private UUID extractSpaceId(Authentication auth) {
        if (auth instanceof TakiboSecurityContextCarrier carrier) {
            return tryParseUuid(carrier.getSecurityContext().tenant().spaceId());
        }
        return null;
    }

    private boolean hasAuthority(Authentication auth, String expected) {
        for (GrantedAuthority ga : auth.getAuthorities()) {
            if (expected.equals(ga.getAuthority())) return true;
        }
        return false;
    }

    private boolean hasAnyAuthority(Authentication auth, String... expected) {
        for (String e : expected) {
            if (hasAuthority(auth, e)) return true;
        }
        return false;
    }

    private UUID tryParseUuid(String s) {
        if (s == null || s.isBlank()) return null;
        try {
            return UUID.fromString(s.trim());
        } catch (Exception e) {
            return null;
        }
    }

    private boolean isPlatformAdmin(Authentication auth) {
        String code = TechnicalRole.PLATFORM_ADMIN.code();
        return hasAnyAuthority(auth,
                code, ROLE_PREFIX + code);
    }

    private record CacheEntry(UUID value, Instant at) {
        boolean isExpired(Duration ttl, Clock clock) { return clock.instant().isAfter(at.plus(ttl)); }
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
