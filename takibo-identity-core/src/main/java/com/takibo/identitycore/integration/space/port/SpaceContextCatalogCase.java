package com.takibo.identitycore.integration.space.port;

import java.util.List;
import java.util.Set;
import java.util.UUID;

public interface SpaceContextCatalogCase {

    List<SpaceContextSummary> findByOrganizationAndIds(UUID organizationId, Set<UUID> spaceIds);
}
