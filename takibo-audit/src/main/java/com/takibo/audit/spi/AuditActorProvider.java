package com.takibo.audit.spi;

import java.util.Optional;
import java.util.UUID;

public interface AuditActorProvider {

    Optional<AuditActor> currentActor();

    record AuditActor(
            UUID accountId,
            UUID userId,
            UUID orgId,
            UUID spaceId,
            String actorType,
            String actorSource
    ) {
    }
}
