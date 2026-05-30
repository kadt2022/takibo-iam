package com.takibo.outbox.jpa;

import com.takibo.outbox.core.model.OutboxStatus;
import com.takibo.outbox.jpa.entity.OutboxMessageEntity;
import com.takibo.outbox.jpa.repository.OutboxMessageRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Testcontainers
@ExtendWith(SpringExtension.class)
@SpringBootTest(classes = OutboxJpaClaimIntegrationTest.TestApp.class)
@EnabledIf("dockerIsAvailable")
class OutboxJpaClaimIntegrationTest {

    static boolean dockerIsAvailable() {
        try {
            return DockerClientFactory.instance().isDockerAvailable();
        } catch (Throwable ignored) {
            return false;
        }
    }

    @Container
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("testdb")
            .withUsername("test")
            .withPassword("test");

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "none");
        registry.add("spring.flyway.enabled", () -> "true");
    }

    @SpringBootApplication(scanBasePackages = "com.takibo.outbox.jpa.repository")
    @EnableJpaRepositories(basePackages = "com.takibo.outbox.jpa.repository")
    @EntityScan(basePackages = "com.takibo.outbox.jpa.entity")
    @Import(com.takibo.outbox.jpa.repository.OutboxMessageClaimRepositoryImpl.class)
    static class TestApp {
    }

    @Autowired
    OutboxMessageRepository repository;

    @BeforeEach
    void setup() {
        repository.deleteAll();
        Instant now = Instant.now();

        for (int i = 0; i < 20; i++) {
            OutboxMessageEntity e = new OutboxMessageEntity();
            e.setId(UUID.randomUUID());
            e.setEventType("E");
            e.setAggregateType("A");
            e.setAggregateId("1");
            e.setPayloadJson("{\"k\":\"v\"}");
            e.setStatus(OutboxStatus.PENDING);
            e.setAttempts(0);
            e.setNextRunAt(now.minusSeconds(1));
            e.setCreatedAt(now.minusSeconds(5));
            e.setUpdatedAt(now.minusSeconds(5));
            repository.save(e);
        }
    }

    @Test
    void claimRunnableIsMultiInstanceSafe() throws Exception {
        Instant now = Instant.now();
        var pool = Executors.newFixedThreadPool(2);

        Set<UUID> claimed1 = new HashSet<>();
        Set<UUID> claimed2 = new HashSet<>();

        CountDownLatch latch = new CountDownLatch(2);

        pool.submit(() -> {
            List<OutboxMessageEntity> batch = repository.claimRunnable(now, now.minusSeconds(300), "i1", 10);
            batch.forEach(m -> claimed1.add(m.getId()));
            latch.countDown();
        });

        pool.submit(() -> {
            List<OutboxMessageEntity> batch = repository.claimRunnable(now, now.minusSeconds(300), "i2", 10);
            batch.forEach(m -> claimed2.add(m.getId()));
            latch.countDown();
        });

        assertTrue(latch.await(10, TimeUnit.SECONDS));
        pool.shutdownNow();

        Set<UUID> intersection = new HashSet<>(claimed1);
        intersection.retainAll(claimed2);

        assertEquals(10, claimed1.size());
        assertEquals(10, claimed2.size());
        assertEquals(0, intersection.size());

        assertEquals(20, repository.countByStatus(OutboxStatus.PROCESSING));
    }
}
