package com.takibo.messaging.infrastructure.jpa;

import com.takibo.messaging.domain.DeliveryStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

public interface MessageDeliveryRepository extends JpaRepository<MessageDeliveryEntity, UUID>, MessageDeliveryClaimRepository {

    long countByStatus(DeliveryStatus status);

    @Transactional
    @Modifying
    @Query("""
            update MessageDeliveryEntity d
            set d.status = :status,
                d.updatedAt = :now,
                d.lockedAt = null,
                d.lockedBy = null
            where d.id = :id
            """)
    int updateStatus(@Param("id") UUID id, @Param("status") DeliveryStatus status, @Param("now") Instant now);

    @Transactional
    @Modifying
    @Query("""
            update MessageDeliveryEntity d
            set d.status = :status,
                d.updatedAt = :now,
                d.attempts = :attempts,
                d.nextRunAt = :nextRunAt,
                d.lastError = :lastError,
                d.lockedAt = null,
                d.lockedBy = null
            where d.id = :id
            """)
    int updateAttempt(@Param("id") UUID id,
                      @Param("status") DeliveryStatus status,
                      @Param("attempts") int attempts,
                      @Param("nextRunAt") Instant nextRunAt,
                      @Param("lastError") String lastError,
                      @Param("now") Instant now);
}
