package com.takibo.managementservice.integration;

import com.takibo.identitycore.integration.space.port.SpaceOwnershipGuardCase;
import com.takibo.managementservice.infrastructure.jpa.repository.SpaceOwnershipRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Implémentation du Port TIS-CORE : vérifie en BDD que port ∈ org.
 * Avec cache mémoire TTL court pour limiter les hits DB (même approche que SpaceStatusCheckerCaseAdapter).
 */
@Component
@RequiredArgsConstructor
public class SpaceOwnershipGuardCaseAdapter implements SpaceOwnershipGuardCase {

    private final SpaceOwnershipRepository repository;

    private final Map<UUID, CacheEntry> cache = new ConcurrentHashMap<>();
    private final Duration ttl = Duration.ofSeconds(10);

    @Override
    public void assertSpaceBelongsToOrg(UUID spaceId) {
        UUID actualOrgId = resolveOrgId(spaceId)
                .orElseThrow(() -> new RuntimeException("TMS-SPACE-NOT-FOUND: spaceId=" + spaceId));

//        if (!(actualOrgId)) {
//            // 403 – mappé par Sentinel
//            throw new AccessDeniedException("ORG_MISMATCH");
//        }
    }

    private Optional<UUID> resolveOrgId(UUID spaceId) {
        CacheEntry hit = cache.get(spaceId);
        if (hit != null && !hit.isExpired(ttl)) {
            return Optional.of(hit.value);
        }
        Optional<UUID> org = repository.findOrgIdBySpaceId(spaceId);
        org.ifPresent(v -> cache.put(spaceId, new CacheEntry(v, Instant.now())));
        return org;
    }

    private record CacheEntry(UUID value, Instant at) {
        boolean isExpired(Duration ttl) { return Instant.now().isAfter(at.plus(ttl)); }
    }
}
