package com.takibo.managementservice.infrastructure.outbox;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@ConditionalOnProperty(prefix = "takibo.space.provisioning", name = "mode", havingValue = "noop", matchIfMissing = true)
public class NoopSpaceProvisioningAdapter implements SpaceProvisioningPort {

    private static final Logger log = LoggerFactory.getLogger(NoopSpaceProvisioningAdapter.class);

    @Override
    public void onSpaceCreated(UUID orgId, UUID spaceId, UUID ownerAccountId, String source, UUID outboxEventId) {
        log.info("SpaceCreated processed | orgId={} spaceId={} ownerAccountId={} source={} outboxEventId={}",
                orgId, spaceId, ownerAccountId, source, outboxEventId);
    }
}
