package com.takibo.managementservice.integration;

import com.takibo.identitycore.domain.exception.SpaceNotFoundException;
import com.takibo.identitycore.integration.space.port.SpaceOwnershipGuardCase;
import com.takibo.managementservice.infrastructure.jpa.repository.SpaceOwnershipRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

// TTL court (10 s) : compromis accepté pour limiter les hits DB tout en gardant une fenêtre de cohérence raisonnable.
@Component
@RequiredArgsConstructor
public class SpaceOwnershipGuardCaseAdapter implements SpaceOwnershipGuardCase {

    private final SpaceOwnershipRepository repository;

    private final Map<UUID, CacheEntry> cache = new ConcurrentHashMap<>();
    private final Duration ttl = Duration.ofSeconds(10);

    @Override
    public void assertSpaceBelongsToOrg(UUID spaceId, UUID expectedOrgId) {
        if (spaceId == null) {
            throw new IllegalArgumentException("spaceId must not be null");
        }
        if (expectedOrgId == null) {
            throw new AccessDeniedException("ORG_CONTEXT_REQUIRED");
        }

        UUID actualOrgId = resolveOrgId(spaceId)
                .orElseThrow(() -> new SpaceNotFoundException(spaceId));

        if (!expectedOrgId.equals(actualOrgId)) {
            throw new AccessDeniedException("ORG_MISMATCH");
        }
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
