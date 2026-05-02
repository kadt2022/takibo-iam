package com.takibo.managementservice.infrastructure.outbox;

import java.util.UUID;

public interface SpaceProvisioningPort {

    void onSpaceCreated(UUID orgId,
                        UUID spaceId,
                        UUID ownerAccountId,
                        String source,
                        UUID outboxEventId);
}
