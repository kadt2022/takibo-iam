package com.takibo.messaging.infrastructure.jpa;

import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifie le contrat Java (liaison des parametres, conversion du resultat) des requetes natives.
 * La semantique SQL elle-meme (CTE, {@code FOR UPDATE SKIP LOCKED}, {@code RETURNING}) est
 * specifique a PostgreSQL et ne peut etre prouvee qu'avec une base reelle.
 */
class MessageDeliveryClaimRepositoryImplTest {

    private static final Instant NOW = Instant.parse("2026-01-06T10:00:00Z");
    private static final Instant STALE = NOW.minusSeconds(30);

    private final Map<String, Object> boundParameters = new LinkedHashMap<>();
    private String executedSql;
    private Class<?> mappedResultClass;
    private Object queryResult;

    @Test
    void claimRunnableBindsParametersAndMapsToEntity() throws Exception {
        MessageDeliveryEntity claimed = new MessageDeliveryEntity();
        queryResult = List.of(claimed);

        List<MessageDeliveryEntity> result = repository().claimRunnable(NOW, STALE, "node-1", 25);

        assertEquals(List.of(claimed), result);
        assertSame(MessageDeliveryEntity.class, mappedResultClass);
        assertEquals(Map.of("now", NOW, "staleBefore", STALE, "lockedBy", "node-1", "batchSize", 25), boundParameters);
    }

    @Test
    void claimRunnableLocksRowsWithoutBlockingConcurrentWorkers() throws Exception {
        queryResult = List.of();

        repository().claimRunnable(NOW, STALE, "node-1", 10);

        String sql = normalizedSql();
        assertTrue(sql.contains("for update skip locked"), "deux workers ne doivent jamais reserver la meme ligne");
        assertTrue(sql.contains("status in ('pending','failed') and next_run_at <= :now"));
        assertTrue(sql.contains("status = 'processing' and locked_at is not null and locked_at <= :stalebefore"));
        assertTrue(sql.contains("set status = 'processing'"));
        assertTrue(sql.contains("returning *"));
    }

    @Test
    void existsEligibleBindsParameters() throws Exception {
        queryResult = Boolean.TRUE;

        assertTrue(repository().existsEligible(NOW, STALE));

        assertEquals(Map.of("now", NOW, "staleBefore", STALE), boundParameters);
        assertTrue(normalizedSql().startsWith("select exists("));
    }

    @Test
    void existsEligibleReturnsFalseForBooleanFalse() throws Exception {
        queryResult = Boolean.FALSE;

        assertFalse(repository().existsEligible(NOW, STALE));
    }

    @Test
    void existsEligibleParsesTextualResultFromDrivers() throws Exception {
        queryResult = "true";
        assertTrue(repository().existsEligible(NOW, STALE));

        queryResult = "t";
        assertFalse(repository().existsEligible(NOW, STALE), "seul 'true' est interprete comme vrai");

        queryResult = null;
        assertFalse(repository().existsEligible(NOW, STALE));
    }

    private String normalizedSql() {
        return executedSql.replaceAll("\\s+", " ").trim().toLowerCase();
    }

    private MessageDeliveryClaimRepositoryImpl repository() throws Exception {
        MessageDeliveryClaimRepositoryImpl repository = new MessageDeliveryClaimRepositoryImpl();
        Field field = MessageDeliveryClaimRepositoryImpl.class.getDeclaredField("entityManager");
        field.setAccessible(true);
        field.set(repository, entityManager());
        return repository;
    }

    private EntityManager entityManager() {
        Query query = (Query) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{Query.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "setParameter" -> {
                        boundParameters.put((String) args[0], args[1]);
                        yield proxy;
                    }
                    case "getResultList", "getSingleResult" -> queryResult;
                    default -> throw new UnsupportedOperationException(method.getName());
                });
        return (EntityManager) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{EntityManager.class},
                (proxy, method, args) -> {
                    if (!method.getName().equals("createNativeQuery")) {
                        throw new UnsupportedOperationException(method.getName());
                    }
                    executedSql = (String) args[0];
                    mappedResultClass = args.length > 1 ? (Class<?>) args[1] : null;
                    return query;
                });
    }
}
