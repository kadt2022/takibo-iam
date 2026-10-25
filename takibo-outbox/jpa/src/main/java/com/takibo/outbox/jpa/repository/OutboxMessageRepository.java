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

    /**
     * Insère un message en ignorant uniquement le conflit sur {@code uq_outbox_dedup_key} : la cible
     * du ON CONFLICT reprend le prédicat de l'index partiel, si bien qu'une collision de clé primaire
     * ou toute autre contrainte échoue toujours. Aucune exception n'est levée sur un doublon, donc la
     * transaction de l'appelant reste utilisable (PostgreSQL n'interrompt pas la transaction).
     *
     * @return 1 si le message est inséré, 0 si un message porte déjà cette clé de déduplication
     */
    @Transactional
    @Modifying
    @Query(value = """
    insert into outbox_messages (id, event_type, aggregate_type, aggregate_id, org_id, space_id, payload_json,
                                 status, attempts, next_run_at, dedup_key, created_at, updated_at)
    values (:#{#m.id}, :#{#m.eventType}, :#{#m.aggregateType}, :#{#m.aggregateId},
            cast(:#{#m.orgId} as uuid), cast(:#{#m.spaceId} as uuid), cast(:#{#m.payloadJson} as jsonb),
            :#{#m.status.name()}, :#{#m.attempts}, :#{#m.nextRunAt}, cast(:#{#m.dedupKey} as varchar),
            :#{#m.createdAt}, :#{#m.updatedAt})
    on conflict (dedup_key) where dedup_key is not null do nothing
    """, nativeQuery = true)
    int insertIgnoringDedupConflict(@Param("m") OutboxMessageEntity message);

    /**
     * Vrai si le message déjà enregistré sous cette clé décrit le même événement : même type, même
     * agrégat, même tenant et même contenu (égalité jsonb, insensible à la mise en forme).
     */
    @Query(value = """
    select exists (
        select 1
        from outbox_messages
        where dedup_key = :#{#m.dedupKey}
          and event_type = :#{#m.eventType}
          and aggregate_type = :#{#m.aggregateType}
          and aggregate_id = :#{#m.aggregateId}
          and org_id is not distinct from cast(:#{#m.orgId} as uuid)
          and space_id is not distinct from cast(:#{#m.spaceId} as uuid)
          and payload_json = cast(:#{#m.payloadJson} as jsonb)
    )
    """, nativeQuery = true)
    boolean existsSameEventForDedupKey(@Param("m") OutboxMessageEntity message);

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
