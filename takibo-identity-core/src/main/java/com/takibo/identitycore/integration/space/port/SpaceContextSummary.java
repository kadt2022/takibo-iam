package com.takibo.identitycore.integration.space.port;

import java.util.UUID;

public record SpaceContextSummary(
        UUID organizationId,
        UUID id,
        String code,
        String name,
        String status
) {
}
