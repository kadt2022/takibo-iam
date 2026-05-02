package com.takibo.outbox.jpa.repository;

import com.takibo.outbox.core.model.OutboxStatus;
import com.takibo.outbox.jpa.entity.OutboxMessageEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

public interface OutboxMessageRepository extends JpaRepository<OutboxMessageEntity, UUID>, OutboxMessageClaimRepository {

    long countByStatus(OutboxStatus status);

    @Query(value = "select coalesce(extract(epoch from (max(now() - created_at))), 0) from outbox_messages where status in ('PENDING','FAILED')", nativeQuery = true)
    double runnableLagSeconds();

    @Transactional
    @Modifying
    @Query("update OutboxMessageEntity m set m.status = :status, m.updatedAt = :now, m.lockedAt = null, m.lockedBy = null where m.id = :id")
    int updateStatus(@Param("id") UUID id, @Param("status") OutboxStatus status, @Param("now") Instant now);

    @Transactional
    @Modifying
    @Query("update OutboxMessageEntity m set m.status = :status, m.updatedAt = :now, m.attempts = :attempts, m.nextRunAt = :nextRunAt, m.lastError = :lastError, m.lockedAt = null, m.lockedBy = null where m.id = :id")
    int failAndScheduleRetry(
            @Param("id") UUID id,
            @Param("status") OutboxStatus status,
            @Param("attempts") int attempts,
            @Param("nextRunAt") Instant nextRunAt,
            @Param("lastError") String lastError,
            @Param("now") Instant now
    );

    @Transactional
    @Modifying
    @Query("update OutboxMessageEntity m set m.status = :status, m.updatedAt = :now, m.attempts = :attempts, m.lastError = :lastError, m.lockedAt = null, m.lockedBy = null where m.id = :id")
    int markDead(
            @Param("id") UUID id,
            @Param("status") OutboxStatus status,
            @Param("attempts") int attempts,
            @Param("lastError") String lastError,
            @Param("now") Instant now
    );

    @Query(value = """
    select exists (
        select 1
        from outbox_messages
        where (
            (status in ('PENDING','FAILED') and next_run_at <= :now)
            or
            (status = 'PROCESSING' and locked_at is not null and locked_at <= :staleBefore)
        )
        limit 1
    )
    """, nativeQuery = true)
    boolean existsEligible(@Param("now") Instant now, @Param("staleBefore") Instant staleBefore);

}
