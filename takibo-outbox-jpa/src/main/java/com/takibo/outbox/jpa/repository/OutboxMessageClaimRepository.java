package com.takibo.outbox.jpa.repository;

import com.takibo.outbox.jpa.entity.OutboxMessageEntity;

import java.time.Instant;
import java.util.List;

public interface OutboxMessageClaimRepository {

    List<OutboxMessageEntity> claimRunnable(Instant now, Instant staleBefore, String lockedBy, int batchSize);

    boolean existsEligible(Instant now, Instant staleBefore);
}
