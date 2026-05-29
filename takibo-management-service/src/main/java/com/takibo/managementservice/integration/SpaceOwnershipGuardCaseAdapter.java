package com.takibo.managementservice.integration;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.takibo.identitycore.domain.exception.SpaceNotFoundException;
import com.takibo.identitycore.integration.space.port.SpaceOwnershipGuardCase;
import com.takibo.managementservice.infrastructure.jpa.repository.SpaceOwnershipRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

// TTL court et taille bornée pour limiter les accès BDD sans conserver indéfiniment des entrées expirées.
@Component
@RequiredArgsConstructor
public class SpaceOwnershipGuardCaseAdapter implements SpaceOwnershipGuardCase {

    private final SpaceOwnershipRepository repository;

    private final Cache<UUID, UUID> orgIdBySpaceId = Caffeine.newBuilder()
            .expireAfterWrite(10, TimeUnit.SECONDS)
            .maximumSize(10_000)
            .build();

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
        UUID cachedOrgId = orgIdBySpaceId.getIfPresent(spaceId);
        if (cachedOrgId != null) {
            return Optional.of(cachedOrgId);
        }

        Optional<UUID> orgId = repository.findOrgIdBySpaceId(spaceId);
        orgId.ifPresent(value -> orgIdBySpaceId.put(spaceId, value));
        return orgId;
    }
}
