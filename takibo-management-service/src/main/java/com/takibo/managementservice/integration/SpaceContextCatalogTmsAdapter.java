package com.takibo.managementservice.integration;

import com.takibo.identitycore.integration.space.port.SpaceContextCatalogCase;
import com.takibo.identitycore.integration.space.port.SpaceContextSummary;
import com.takibo.managementservice.infrastructure.jpa.repository.JpaSpaceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SpaceContextCatalogTmsAdapter implements SpaceContextCatalogCase {

    private final JpaSpaceRepository spaces;

    @Override
    public List<SpaceContextSummary> findByOrganizationAndIds(UUID organizationId, Set<UUID> spaceIds) {
        if (spaceIds == null || spaceIds.isEmpty()) {
            return List.of();
        }

        return spaces.findByOrgIdAndIdIn(organizationId, spaceIds).stream()
                .map(space -> new SpaceContextSummary(
                        space.getOrgId(),
                        space.getId(),
                        space.getCode(),
                        space.getName(),
                        space.getStatus().name()))
                .toList();
    }
}
