package com.takibo.messaging.infrastructure.jpa;

import java.time.Instant;
import java.util.List;

public interface MessageDeliveryClaimRepository {

    List<MessageDeliveryEntity> claimRunnable(Instant now, Instant staleBefore, String lockedBy, int batchSize);

    boolean existsEligible(Instant now, Instant staleBefore);
}
