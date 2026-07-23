package com.takibo.managementservice.application.port;

import java.util.Optional;
import java.util.UUID;

public interface SpaceLookupPort {

    boolean existsById(UUID spaceId);

    Optional<UUID> findOrganizationId(UUID spaceId);
}
