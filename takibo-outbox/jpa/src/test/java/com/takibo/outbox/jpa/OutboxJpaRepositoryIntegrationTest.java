package com.takibo.outbox.jpa;

import com.takibo.outbox.core.model.OutboxEnvelope;
import com.takibo.outbox.core.model.OutboxStatus;
import com.takibo.outbox.jpa.entity.OutboxMessageEntity;
import com.takibo.outbox.jpa.mapper.OutboxJpaMapperImpl;
import com.takibo.outbox.jpa.publisher.JpaOutboxPublisher;
import com.takibo.outbox.jpa.publisher.OutboxDedupKeyCollisionException;
import com.takibo.outbox.jpa.repository.OutboxMessageRepository;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.PostgreSQLContainer;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Sémantique SQL de l'outbox sur un vrai PostgreSQL, avec le schéma de la migration Flyway de test :
 * éligibilité au claim, reprise des verrous périmés, transitions d'état, déduplication et métriques.
 *
 * PostgreSQL vient de Testcontainers quand Docker est disponible (CI). Sans Docker, on peut fournir
 * une base existante via TAKIBO_OUTBOX_IT_JDBC_URL / _USERNAME / _PASSWORD ; sinon le test est ignoré.
 * Dans les deux cas, le test travaille dans un schéma jetable créé pour l'exécution puis supprimé :
 * il ne lit ni n'efface jamais les données déjà présentes dans la base.
 */
@SpringBootTest(classes = OutboxJpaRepositoryIntegrationTest.TestApp.class)
@EnabledIf("databaseAvailable")
class OutboxJpaRepositoryIntegrationTest {

    private static final String EXTERNAL_URL = System.getenv("TAKIBO_OUTBOX_IT_JDBC_URL");
    private static final String SCHEMA = "outbox_it_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    private static PostgreSQLContainer<?> postgres;
    private static Flyway flyway;

    private static final Instant NOW = Instant.now().truncatedTo(ChronoUnit.MILLIS);
    private static final Instant STALE_BEFORE = NOW.minusSeconds(300);

    static boolean databaseAvailable() {
        if (EXTERNAL_URL != null && !EXTERNAL_URL.isBlank()) {
            return true;
        }
        boolean available;
        try {
            available = DockerClientFactory.instance().isDockerAvailable();
        } catch (Throwable ignored) {
            available = false;
        }
        // En CI, Docker est obligatoire : la classe échoue au lieu d'être ignorée.
        if (!available && "true".equalsIgnoreCase(System.getenv("CI"))) {
            throw new IllegalStateException(
                    "Docker indisponible en CI : les tests PostgreSQL ne peuvent pas être ignorés");
        }
        return available;
    }

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry registry) {
        String url;
        String username;
        String password;
        if (EXTERNAL_URL != null && !EXTERNAL_URL.isBlank()) {
            url = EXTERNAL_URL;
            username = System.getenv().getOrDefault("TAKIBO_OUTBOX_IT_USERNAME", "postgres");
            password = System.getenv().getOrDefault("TAKIBO_OUTBOX_IT_PASSWORD", "postgres");
        } else {
            postgres = new PostgreSQLContainer<>("postgres:16-alpine")
                    .withDatabaseName("outbox").withUsername("test").withPassword("test");
            postgres.start();
            url = postgres.getJdbcUrl();
            username = postgres.getUsername();
            password = postgres.getPassword();
        }
        flyway = Flyway.configure().dataSource(url, username, password)
                .schemas(SCHEMA).createSchemas(true)
                .locations("classpath:db/migration").cleanDisabled(false).load();
        flyway.migrate();
        String isolatedUrl = url + (url.contains("?") ? "&" : "?") + "currentSchema=" + SCHEMA;
        registry.add("spring.datasource.url", () -> isolatedUrl);
        registry.add("spring.datasource.username", () -> username);
        registry.add("spring.datasource.password", () -> password);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "none");
        registry.add("spring.flyway.enabled", () -> "false");
    }

    @AfterAll
    static void dropIsolatedSchema() {
        if (flyway != null) {
            flyway.clean();
        }
        if (postgres != null) {
            postgres.stop();
        }
    }

    @SpringBootApplication(scanBasePackages = "com.takibo.outbox.jpa.repository")
    @EnableJpaRepositories(basePackages = "com.takibo.outbox.jpa.repository")
    @EntityScan(basePackages = "com.takibo.outbox.jpa.entity")
    @Import(com.takibo.outbox.jpa.repository.OutboxMessageClaimRepositoryImpl.class)
    static class TestApp {
    }

    @Autowired
    OutboxMessageRepository repository;

    @Autowired
    PlatformTransactionManager transactionManager;

    @Autowired
    JdbcTemplate jdbc;

    @BeforeEach
    void clean() {
        repository.deleteAll();
        jdbc.execute("create table if not exists it_business_writes (id varchar(40) primary key)");
        jdbc.update("delete from it_business_writes");
    }

    private OutboxMessageEntity save(String aggregateId, OutboxStatus status, Instant nextRunAt, Instant lockedAt, String dedupKey) {
        OutboxMessageEntity e = new OutboxMessageEntity();
        e.setId(UUID.randomUUID());
        e.setEventType("E");
        e.setAggregateType("A");
        e.setAggregateId(aggregateId);
        e.setPayloadJson("{\"k\":\"v\"}");
        e.setStatus(status);
        e.setAttempts(0);
        e.setNextRunAt(nextRunAt);
        e.setLockedAt(lockedAt);
        e.setLockedBy(lockedAt == null ? null : "old-node");
        e.setDedupKey(dedupKey);
        e.setCreatedAt(NOW.minusSeconds(30));
        e.setUpdatedAt(NOW.minusSeconds(30));
        return repository.saveAndFlush(e);
    }

    private OutboxMessageEntity save(String aggregateId, OutboxStatus status, Instant nextRunAt, Instant lockedAt) {
        return save(aggregateId, status, nextRunAt, lockedAt, null);
    }

    private List<OutboxMessageEntity> claim(String lockedBy, int batchSize) {
        return new TransactionTemplate(transactionManager).execute(s ->
                repository.claimRunnable(NOW, STALE_BEFORE, lockedBy, batchSize));
    }

    private OutboxMessageEntity reload(OutboxMessageEntity e) {
        return repository.findById(e.getId()).orElseThrow();
    }

    @Test
    void claim_takesOnlyDuePendingAndFailedMessagesAndStaleLocks() {
        save("pending-due", OutboxStatus.PENDING, NOW.minusSeconds(1), null);
        save("failed-due", OutboxStatus.FAILED, NOW.minusSeconds(1), null);
        save("pending-future", OutboxStatus.PENDING, NOW.plusSeconds(60), null);
        save("failed-future", OutboxStatus.FAILED, NOW.plusSeconds(60), null);
        save("processing-stale", OutboxStatus.PROCESSING, NOW.minusSeconds(1), NOW.minusSeconds(600));
        save("processing-fresh", OutboxStatus.PROCESSING, NOW.minusSeconds(1), NOW.minusSeconds(10));
        save("processing-unlocked", OutboxStatus.PROCESSING, NOW.minusSeconds(1), null);
        save("processed", OutboxStatus.PROCESSED, NOW.minusSeconds(1), null);
        save("dead", OutboxStatus.DEAD, NOW.minusSeconds(1), null);

        List<OutboxMessageEntity> claimed = claim("node-1", 50);

        assertThat(claimed).extracting(OutboxMessageEntity::getAggregateId)
                .containsExactlyInAnyOrder("pending-due", "failed-due", "processing-stale");
        assertThat(claimed).allSatisfy(m -> {
            assertThat(m.getStatus()).isEqualTo(OutboxStatus.PROCESSING);
            assertThat(m.getLockedBy()).isEqualTo("node-1");
            assertThat(m.getLockedAt()).isEqualTo(NOW);
            assertThat(m.getUpdatedAt()).isEqualTo(NOW);
        });
        assertThat(repository.countByStatus(OutboxStatus.PROCESSING)).isEqualTo(5);
    }

    @Test
    void claim_respectsBatchSize_andTakesOldestNextRunFirst() {
        save("third", OutboxStatus.PENDING, NOW.minusSeconds(10), null);
        save("first", OutboxStatus.PENDING, NOW.minusSeconds(30), null);
        save("second", OutboxStatus.PENDING, NOW.minusSeconds(20), null);

        assertThat(claim("node-1", 2)).extracting(OutboxMessageEntity::getAggregateId)
                .containsExactlyInAnyOrder("first", "second");
        assertThat(claim("node-2", 2)).extracting(OutboxMessageEntity::getAggregateId).containsExactly("third");
        assertThat(claim("node-3", 2)).isEmpty();
    }

    @Test
    void existsEligible_matchesTheClaimRules() {
        assertThat(repository.existsEligible(NOW, STALE_BEFORE)).isFalse();

        save("future", OutboxStatus.PENDING, NOW.plusSeconds(60), null);
        save("fresh-lock", OutboxStatus.PROCESSING, NOW.minusSeconds(1), NOW.minusSeconds(10));
        save("done", OutboxStatus.PROCESSED, NOW.minusSeconds(1), null);
        assertThat(repository.existsEligible(NOW, STALE_BEFORE)).isFalse();

        save("stale-lock", OutboxStatus.PROCESSING, NOW.minusSeconds(1), NOW.minusSeconds(600));
        assertThat(repository.existsEligible(NOW, STALE_BEFORE)).isTrue();
    }

    @Test
    void stateTransitions_releaseTheLockAndRecordTheOutcome() {
        OutboxMessageEntity processed = save("processed", OutboxStatus.PROCESSING, NOW, NOW);
        OutboxMessageEntity retried = save("retried", OutboxStatus.PROCESSING, NOW, NOW);
        OutboxMessageEntity dead = save("dead", OutboxStatus.PROCESSING, NOW, NOW);
        Instant later = NOW.plusSeconds(5);

        assertThat(repository.updateStatus(processed.getId(), OutboxStatus.PROCESSED, later)).isEqualTo(1);
        assertThat(repository.failAndScheduleRetry(retried.getId(), OutboxStatus.FAILED, 2, later.plusSeconds(2), "boom", later)).isEqualTo(1);
        assertThat(repository.markDead(dead.getId(), OutboxStatus.DEAD, 9, "maxAttempts reached", later)).isEqualTo(1);

        OutboxMessageEntity p = reload(processed);
        assertThat(p.getStatus()).isEqualTo(OutboxStatus.PROCESSED);
        assertThat(p.getLockedAt()).isNull();
        assertThat(p.getLockedBy()).isNull();
        assertThat(p.getUpdatedAt()).isEqualTo(later);

        OutboxMessageEntity r = reload(retried);
        assertThat(r.getStatus()).isEqualTo(OutboxStatus.FAILED);
        assertThat(r.getAttempts()).isEqualTo(2);
        assertThat(r.getNextRunAt()).isEqualTo(later.plusSeconds(2));
        assertThat(r.getLastError()).isEqualTo("boom");
        assertThat(r.getLockedBy()).isNull();

        OutboxMessageEntity d = reload(dead);
        assertThat(d.getStatus()).isEqualTo(OutboxStatus.DEAD);
        assertThat(d.getAttempts()).isEqualTo(9);
        assertThat(d.getLastError()).isEqualTo("maxAttempts reached");
        assertThat(d.getLockedAt()).isNull();

        assertThat(repository.updateStatus(UUID.randomUUID(), OutboxStatus.PROCESSED, later)).isZero();
    }

    @Test
    void failedMessageBecomesClaimableAgainOnlyAfterItsRetryTime() {
        OutboxMessageEntity m = save("retry", OutboxStatus.PROCESSING, NOW, NOW);
        repository.failAndScheduleRetry(m.getId(), OutboxStatus.FAILED, 1, NOW.plusSeconds(2), "boom", NOW);

        assertThat(claim("node-1", 10)).isEmpty();
        List<OutboxMessageEntity> afterRetryTime = new TransactionTemplate(transactionManager).execute(s ->
                repository.claimRunnable(NOW.plusSeconds(2), STALE_BEFORE, "node-1", 10));
        assertThat(afterRetryTime).extracting(OutboxMessageEntity::getAggregateId).containsExactly("retry");
    }

    // ── Déduplication dans la transaction de l'appelant ─────────────────────────────────────
    // Chaque scénario écrit aussi une ligne « métier » dans la même transaction : c'est elle qui
    // prouve que la transaction de l'appelant est validée (doublon légitime) ou annulée (anomalie).

    private JpaOutboxPublisher publisher() {
        return new JpaOutboxPublisher(repository, Clock.fixed(NOW, ZoneOffset.UTC), new OutboxJpaMapperImpl());
    }

    private static OutboxEnvelope event(String aggregateId, String payload, String dedupKey) {
        return OutboxEnvelope.of("E", "A", aggregateId, null, null, payload, dedupKey);
    }

    private void businessWrite(String id) {
        jdbc.update("insert into it_business_writes(id) values (?)", id);
    }

    private List<String> businessWrites() {
        return jdbc.queryForList("select id from it_business_writes order by id", String.class);
    }

    private List<String> messagesWithKey(String dedupKey) {
        return jdbc.queryForList("select aggregate_id from outbox_messages where dedup_key = ?", String.class, dedupKey);
    }

    @Test
    void duplicateOfTheSameEvent_isIgnored_andTheCallerTransactionCommits() {
        JpaOutboxPublisher publisher = publisher();
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        tx.executeWithoutResult(s -> publisher.publish(event("1", "{\"n\":1}", "same-key")));

        // Même événement republié ; le JSON diffère seulement par sa mise en forme (égalité jsonb).
        tx.executeWithoutResult(s -> {
            publisher.publish(event("1", "{ \"n\" : 1 }", "same-key"));
            businessWrite("after-duplicate");
        });

        assertThat(businessWrites()).containsExactly("after-duplicate");
        assertThat(messagesWithKey("same-key")).containsExactly("1");
    }

    @Test
    void duplicateOutsideACallerTransaction_isIgnored() {
        JpaOutboxPublisher publisher = publisher();
        publisher.publish(event("1", "{\"n\":1}", "same-key"));

        assertThatCode(() -> publisher.publish(event("1", "{\"n\":1}", "same-key"))).doesNotThrowAnyException();
        assertThat(messagesWithKey("same-key")).containsExactly("1");
    }

    @Test
    void sameDedupKeyForADifferentEvent_failsAndRollsBackTheCallerTransaction() {
        JpaOutboxPublisher publisher = publisher();
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        tx.executeWithoutResult(s -> publisher.publish(event("1", "{\"n\":1}", "same-key")));

        assertThatThrownBy(() -> tx.executeWithoutResult(s -> {
            businessWrite("must-roll-back");
            publisher.publish(event("1", "{\"n\":2}", "same-key"));
        })).isInstanceOf(OutboxDedupKeyCollisionException.class);

        assertThatThrownBy(() -> tx.executeWithoutResult(s ->
                publisher.publish(event("other-aggregate", "{\"n\":1}", "same-key"))))
                .isInstanceOf(OutboxDedupKeyCollisionException.class);

        assertThat(businessWrites()).isEmpty();
        assertThat(messagesWithKey("same-key")).containsExactly("1");
    }

    @Test
    void primaryKeyCollision_isNeverTreatedAsADuplicate_andRollsBackTheCallerTransaction() {
        JpaOutboxPublisher publisher = publisher();
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        OutboxEnvelope first = event("1", "{\"n\":1}", "key-1");
        tx.executeWithoutResult(s -> publisher.publish(first));

        OutboxEnvelope sameId = new OutboxEnvelope(first.id(), "E", "A", "2", null, null, "{\"n\":2}", "key-2", NOW);
        assertThatThrownBy(() -> tx.executeWithoutResult(s -> {
            businessWrite("must-roll-back");
            publisher.publish(sameId);
        })).isInstanceOf(DataIntegrityViolationException.class);

        assertThat(businessWrites()).isEmpty();
        assertThat(repository.findAll()).extracting(OutboxMessageEntity::getAggregateId).containsExactly("1");
    }

    @Test
    void anyOtherPersistenceFailure_rollsBackTheCallerTransaction() {
        JpaOutboxPublisher publisher = publisher();
        TransactionTemplate tx = new TransactionTemplate(transactionManager);

        assertThatThrownBy(() -> tx.executeWithoutResult(s -> {
            businessWrite("must-roll-back");
            publisher.publish(event("1", "not json", "key-1"));
        })).isInstanceOf(DataAccessException.class);

        assertThat(businessWrites()).isEmpty();
        assertThat(repository.count()).isZero();
    }

    @Test
    void messagesWithoutDedupKey_areNeverDeduplicated() {
        JpaOutboxPublisher publisher = publisher();
        publisher.publish(event("1", "{\"n\":1}", null));
        publisher.publish(event("1", "{\"n\":1}", null));

        assertThat(repository.count()).isEqualTo(2);
    }

    @Test
    void concurrentDuplicate_waitsForTheFirstTransaction_thenIsIgnoredOnceItCommits() throws Exception {
        String winner = raceOnTheSameKey(true);

        assertThat(winner).isEqualTo("first");
        assertThat(businessWrites()).containsExactly("first", "second");
    }

    @Test
    void concurrentDuplicate_isInsertedWhenTheFirstTransactionRollsBack() throws Exception {
        String winner = raceOnTheSameKey(false);

        assertThat(winner).isEqualTo("second");
        assertThat(businessWrites()).containsExactly("second");
    }

    /**
     * Deux transactions publient la même clé. La première insère puis attend ; la seconde doit rester
     * bloquée par PostgreSQL sur l'index unique tant que la première n'est pas terminée. Selon que la
     * première valide ou annule, la seconde ignore le doublon ou insère le message ; dans les deux cas
     * sa propre écriture métier est validée.
     *
     * @return « first » ou « second » : la transaction dont le message est finalement enregistré
     */
    private String raceOnTheSameKey(boolean firstCommits) throws Exception {
        JpaOutboxPublisher publisher = publisher();
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        OutboxEnvelope firstEvent = event("agg", "{\"n\":1}", "race-key");
        OutboxEnvelope secondEvent = event("agg", "{\"n\":1}", "race-key");
        CountDownLatch firstInserted = new CountDownLatch(1);
        CountDownLatch releaseFirst = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<?> first = pool.submit(() -> tx.executeWithoutResult(s -> {
                publisher.publish(firstEvent);
                businessWrite("first");
                firstInserted.countDown();
                await(releaseFirst);
                if (!firstCommits) {
                    s.setRollbackOnly();
                }
            }));
            assertThat(firstInserted.await(10, TimeUnit.SECONDS)).isTrue();

            Future<?> second = pool.submit(() -> tx.executeWithoutResult(s -> {
                publisher.publish(secondEvent);
                businessWrite("second");
            }));
            assertThatThrownBy(() -> second.get(500, TimeUnit.MILLISECONDS))
                    .as("second transaction must wait on the unique index")
                    .isInstanceOf(TimeoutException.class);
            assertThat(jdbc.queryForObject(
                    "select count(*) from pg_stat_activity"
                            + " where wait_event_type = 'Lock' and query like '%insert into outbox_messages%'",
                    Integer.class)).isEqualTo(1);

            releaseFirst.countDown();
            first.get(10, TimeUnit.SECONDS);
            second.get(10, TimeUnit.SECONDS);
        } finally {
            releaseFirst.countDown();
            pool.shutdownNow();
        }
        List<UUID> stored = jdbc.queryForList(
                "select id from outbox_messages where dedup_key = 'race-key'", UUID.class);
        assertThat(stored).hasSize(1);
        return stored.get(0).equals(firstEvent.id()) ? "first" : "second";
    }

    private static void await(CountDownLatch latch) {
        try {
            assertThat(latch.await(10, TimeUnit.SECONDS)).isTrue();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }

    @Test
    void runnableLag_isTheAgeOfTheOldestPendingOrFailedMessage() {
        assertThat(repository.runnableLagSeconds()).isZero();

        save("pending", OutboxStatus.PENDING, NOW, null);
        OutboxMessageEntity processed = save("processed", OutboxStatus.PROCESSED, NOW, null);
        processed.setCreatedAt(Instant.now().minusSeconds(3600));
        repository.saveAndFlush(processed);

        assertThat(repository.runnableLagSeconds()).isBetween(25.0, 600.0);
    }
}
