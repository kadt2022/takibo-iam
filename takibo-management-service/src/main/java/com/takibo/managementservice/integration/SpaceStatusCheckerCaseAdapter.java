package com.takibo.managementservice.integration;

import com.takibo.identitycore.integration.space.port.SpaceStatusCheckerCase;
import com.takibo.identitycore.domain.status.SpaceOperationalStatus;
import com.takibo.managementservice.application.service.SpaceStatusService;
import com.takibo.managementservice.domain.model.SpaceStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Implémentation du Port de TIS-CORE.
 * Inclut un petit cache mémoire (TTL court) pour réduire les hits DB.
 */
@Component
@RequiredArgsConstructor
public class SpaceStatusCheckerCaseAdapter implements SpaceStatusCheckerCase {

    private final SpaceStatusService statusService;
    private final SpaceStatusMapper mapper;

    private final Map<UUID, CacheEntry> cache = new ConcurrentHashMap<>();
    private final Duration ttl = Duration.ofSeconds(10);

    @Override
    public Optional<SpaceOperationalStatus> findStatus(UUID spaceId) {
        CacheEntry hit = cache.get(spaceId);
        if (hit != null && !hit.isExpired(ttl)) {
            return Optional.of(hit.value);
        }
        Optional<SpaceStatus> raw = statusService.findStatus(spaceId);
        Optional<SpaceOperationalStatus> mapped = raw.map(mapper::toCoreStatus);
        mapped.ifPresent(v -> cache.put(spaceId, new CacheEntry(v, Instant.now())));
        return mapped;
    }

    private record CacheEntry(SpaceOperationalStatus value, Instant at) {
        boolean isExpired(Duration ttl) { return Instant.now().isAfter(at.plus(ttl)); }
    }
}
