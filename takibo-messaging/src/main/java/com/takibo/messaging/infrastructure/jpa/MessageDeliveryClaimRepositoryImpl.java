package com.takibo.messaging.infrastructure.jpa;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Repository
public class MessageDeliveryClaimRepositoryImpl implements MessageDeliveryClaimRepository {

    @PersistenceContext
    private EntityManager entityManager;

    @Override
    @Transactional
    @SuppressWarnings("unchecked")
    public List<MessageDeliveryEntity> claimRunnable(Instant now, Instant staleBefore, String lockedBy, int batchSize) {
        String sql = """
                with cte as (
                    select id
                    from message_deliveries
                    where (
                      (status in ('PENDING','FAILED') and next_run_at <= :now)
                      or (status = 'PROCESSING' and locked_at is not null and locked_at <= :staleBefore)
                  )
                    order by next_run_at asc
                    limit :batchSize
                    for update skip locked
                )
                update message_deliveries m
                set status = 'PROCESSING',
                    locked_at = :now,
                    locked_by = :lockedBy,
                    updated_at = :now
                where m.id in (select id from cte)
                returning *
                """;

        return entityManager.createNativeQuery(sql, MessageDeliveryEntity.class)
                .setParameter("now", now)
                .setParameter("staleBefore", staleBefore)
                .setParameter("lockedBy", lockedBy)
                .setParameter("batchSize", batchSize)
                .getResultList();
    }

    @Override
    @Transactional(readOnly = true)
    public boolean existsEligible(Instant now, Instant staleBefore) {
        String sql = """
                select exists(
                    select 1
                    from message_deliveries
                    where (
                      (status in ('PENDING','FAILED') and next_run_at <= :now)
                      or (status = 'PROCESSING' and locked_at is not null and locked_at <= :staleBefore)
                    )
                )
                """;
        Object result = entityManager.createNativeQuery(sql)
                .setParameter("now", now)
                .setParameter("staleBefore", staleBefore)
                .getSingleResult();

        return result instanceof Boolean b ? b : Boolean.parseBoolean(String.valueOf(result));
    }
}
