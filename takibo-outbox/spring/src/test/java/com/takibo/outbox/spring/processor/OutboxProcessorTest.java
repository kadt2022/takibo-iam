package com.takibo.outbox.spring.processor;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.takibo.outbox.core.model.OutboxEnvelope;
import com.takibo.outbox.core.model.OutboxStatus;
import com.takibo.outbox.core.port.OutboxHandler;
import com.takibo.outbox.jpa.entity.OutboxMessageEntity;
import com.takibo.outbox.jpa.mapper.OutboxJpaMapperImpl;
import com.takibo.outbox.jpa.repository.OutboxMessageRepository;
import com.takibo.outbox.spring.backoff.ExponentialOutboxBackoffPolicy;
import com.takibo.outbox.spring.config.OutboxProperties;
import com.takibo.outbox.spring.registry.OutboxHandlerRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.PlatformTransactionManager;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Comportement du processeur : claim, appel du handler, succès, retries avec backoff, dead-letter,
 * métriques et frontières de transaction. Le repository est simulé (sa sémantique SQL est vérifiée
 * sur PostgreSQL dans le module jpa) ; le mapper, le registre et le backoff sont les vrais.
 */
class OutboxProcessorTest {

    private static final Instant NOW = Instant.parse("2026-10-22T10:00:00Z");

    private final OutboxMessageRepository repository = mock(OutboxMessageRepository.class);
    private final PlatformTransactionManager transactionManager = mock(PlatformTransactionManager.class);
    private final SimpleMeterRegistry meters = new SimpleMeterRegistry();
    private final OutboxProperties properties = new OutboxProperties();
    private final List<OutboxEnvelope> handled = new ArrayList<>();

    private ListAppender<ILoggingEvent> logs;

    @BeforeEach
    void setUp() {
        properties.getProcessor().setLockedBy("node-1");
        properties.getProcessor().setBatchSize(25);
        properties.getProcessor().setMaxAttempts(3);
        properties.getProcessor().setLockTimeout(Duration.ofMinutes(5));
        when(repository.existsEligible(any(), any())).thenReturn(true);
        logs = new ListAppender<>();
        logs.start();
        ((Logger) LoggerFactory.getLogger(OutboxProcessor.class)).addAppender(logs);
    }

    @AfterEach
    void tearDown() {
        ((Logger) LoggerFactory.getLogger(OutboxProcessor.class)).detachAppender(logs);
    }

    private OutboxProcessor processor(OutboxHandler... handlers) {
        return new OutboxProcessor(repository, new OutboxHandlerRegistry(Arrays.asList(handlers)),
                new ExponentialOutboxBackoffPolicy(Duration.ofSeconds(1), Duration.ofSeconds(60), 2),
                properties, Clock.fixed(NOW, ZoneOffset.UTC), transactionManager, meters, new OutboxJpaMapperImpl());
    }

    private OutboxHandler handler(String type) {
        return new OutboxHandler() {
            @Override public String eventType() { return type; }
            @Override public void handle(OutboxEnvelope envelope) { handled.add(envelope); }
        };
    }

    private static OutboxHandler failing(String type, Exception error) {
        return new OutboxHandler() {
            @Override public String eventType() { return type; }
            @Override public void handle(OutboxEnvelope envelope) throws Exception { throw error; }
        };
    }

    private static OutboxMessageEntity message(String eventType, int attempts, String payload) {
        OutboxMessageEntity e = new OutboxMessageEntity();
        e.setId(UUID.randomUUID());
        e.setEventType(eventType);
        e.setAggregateType("SPACE");
        e.setAggregateId("s-1");
        e.setPayloadJson(payload);
        e.setStatus(OutboxStatus.PROCESSING);
        e.setAttempts(attempts);
        e.setNextRunAt(NOW);
        e.setCreatedAt(NOW.minusSeconds(10));
        e.setUpdatedAt(NOW);
        return e;
    }

    private void claims(OutboxMessageEntity... batch) {
        when(repository.claimRunnable(any(), any(), any(), anyInt())).thenReturn(List.of(batch));
    }

    private double counter(String name, String eventType) {
        var c = meters.find(name).tag("eventType", eventType).counter();
        return c == null ? 0 : c.count();
    }

    private String logged() {
        return logs.list.stream().map(ILoggingEvent::getFormattedMessage).reduce("", (a, b) -> a + "\n" + b);
    }

    @Test
    void disabledOutbox_doesNothing() {
        properties.setEnabled(false);

        assertThat(processor().processOnce()).isZero();
        verifyNoInteractions(repository, transactionManager);
    }

    @Test
    void nothingEligible_skipsTheClaimTransaction() {
        when(repository.existsEligible(any(), any())).thenReturn(false);

        assertThat(processor().processOnce()).isZero();
        verify(repository).existsEligible(NOW, NOW.minus(Duration.ofMinutes(5)));
        verify(repository, never()).claimRunnable(any(), any(), any(), anyInt());
        verifyNoInteractions(transactionManager);
    }

    @Test
    void claim_usesLockOwnerBatchSizeAndStaleLockTimeout_inItsOwnTransaction() {
        claims();

        assertThat(processor().processOnce()).isZero();
        verify(repository).claimRunnable(NOW, NOW.minus(Duration.ofMinutes(5)), "node-1", 25);
        verify(transactionManager).getTransaction(any());
        verify(transactionManager).commit(any());
    }

    @Test
    void nullClaimResult_isTreatedAsEmpty() {
        when(repository.claimRunnable(any(), any(), any(), anyInt())).thenReturn(null);

        assertThat(processor().processOnce()).isZero();
        verify(repository, never()).updateStatus(any(), any(), any());
    }

    @Test
    void handledMessage_isMarkedProcessed_inASeparateTransactionAfterTheHandler() {
        OutboxMessageEntity m = message("SPACE_CREATED", 0, "{\"k\":1}");
        claims(m);

        assertThat(processor(handler("SPACE_CREATED")).processOnce()).isEqualTo(1);

        assertThat(handled).singleElement().satisfies(e -> {
            assertThat(e.id()).isEqualTo(m.getId());
            assertThat(e.payloadJson()).isEqualTo("{\"k\":1}");
        });
        verify(repository).updateStatus(m.getId(), OutboxStatus.PROCESSED, NOW);
        verify(transactionManager, times(2)).commit(any());
        assertThat(counter("takibo_outbox_processed_total", "SPACE_CREATED")).isEqualTo(1.0);
    }

    @Test
    void handlerLookupTrimsTheEventType() {
        claims(message(" SPACE_CREATED ", 0, "{}"));

        processor(handler("SPACE_CREATED")).processOnce();

        assertThat(handled).hasSize(1);
    }

    @Test
    void everyMessageOfTheBatchIsProcessedIndependently() {
        OutboxMessageEntity ok = message("OK", 0, "{}");
        OutboxMessageEntity ko = message("KO", 0, "{}");
        OutboxMessageEntity unknown = message("UNKNOWN", 0, "{}");
        claims(ok, ko, unknown);

        assertThat(processor(handler("OK"), failing("KO", new IllegalStateException("down"))).processOnce()).isEqualTo(3);

        verify(repository).updateStatus(ok.getId(), OutboxStatus.PROCESSED, NOW);
        verify(repository).failAndScheduleRetry(eq(ko.getId()), eq(OutboxStatus.FAILED), eq(1), any(), eq("down"), eq(NOW));
        verify(repository).markDead(eq(unknown.getId()), eq(OutboxStatus.DEAD), eq(0), any(), eq(NOW));
    }

    @Test
    void unsupportedEventType_isDeadLetteredImmediately() {
        OutboxMessageEntity m = message("NOBODY_HANDLES_THIS", 2, "{}");
        claims(m);

        processor(handler("SPACE_CREATED")).processOnce();

        verify(repository).markDead(m.getId(), OutboxStatus.DEAD, 2, "Unsupported eventType: NOBODY_HANDLES_THIS", NOW);
        verify(repository, never()).failAndScheduleRetry(any(), any(), anyInt(), any(), any(), any());
        assertThat(logged()).contains("Outbox message dead-lettered").doesNotContain("payload=");
        // Constat signalé : ce chemin n'incrémente pas takibo_outbox_dead_total (seul maxAttempts le fait).
        assertThat(counter("takibo_outbox_dead_total", "NOBODY_HANDLES_THIS")).isZero();
    }

    @Test
    void failure_schedulesARetryWithExponentialBackoff() {
        OutboxMessageEntity first = message("KO", 0, "{}");
        OutboxMessageEntity second = message("KO", 1, "{}");
        claims(first, second);

        processor(failing("KO", new IllegalStateException("provisioning down"))).processOnce();

        verify(repository).failAndScheduleRetry(first.getId(), OutboxStatus.FAILED, 1, NOW.plusSeconds(1), "provisioning down", NOW);
        verify(repository).failAndScheduleRetry(second.getId(), OutboxStatus.FAILED, 2, NOW.plusSeconds(2), "provisioning down", NOW);
        assertThat(counter("takibo_outbox_failed_total", "KO")).isEqualTo(2.0);
        assertThat(logged()).contains("Outbox message failed");
    }

    @Test
    void failureWithoutMessage_recordsTheExceptionType_andLongErrorsAreTruncatedTo500() {
        OutboxMessageEntity blank = message("BLANK", 0, "{}");
        OutboxMessageEntity none = message("NONE", 0, "{}");
        OutboxMessageEntity longError = message("LONG", 0, "{}");
        claims(blank, none, longError);

        processor(failing("BLANK", new IllegalStateException("  ")),
                failing("NONE", new UnsupportedOperationException()),
                failing("LONG", new IllegalStateException("x".repeat(600)))).processOnce();

        verify(repository).failAndScheduleRetry(eq(blank.getId()), any(), anyInt(), any(), eq("IllegalStateException"), any());
        verify(repository).failAndScheduleRetry(eq(none.getId()), any(), anyInt(), any(), eq("UnsupportedOperationException"), any());
        verify(repository).failAndScheduleRetry(eq(longError.getId()), any(), anyInt(), any(), eq("x".repeat(500)), any());
    }

    @Test
    void lastAllowedAttempt_isDeadLetteredWithItsLastError() {
        OutboxMessageEntity m = message("KO", 2, "{}");   // 3e tentative, maxAttempts = 3
        claims(m);

        processor(failing("KO", new IllegalStateException("still down"))).processOnce();

        // Constat signalé : la colonne attempts garde la valeur précédente (2) et non 3.
        verify(repository).markDead(m.getId(), OutboxStatus.DEAD, 2, "maxAttempts reached; lastError=still down", NOW);
        verify(repository, never()).failAndScheduleRetry(any(), any(), anyInt(), any(), any(), any());
        assertThat(counter("takibo_outbox_dead_total", "KO")).isEqualTo(1.0);
        assertThat(counter("takibo_outbox_failed_total", "KO")).isZero();
    }

    @Test
    void deadLetterReason_isTruncatedTo500() {
        OutboxMessageEntity m = message("T".repeat(600), 0, "{}");
        claims(m);

        processor().processOnce();

        verify(repository).markDead(eq(m.getId()), eq(OutboxStatus.DEAD), eq(0), eq(("Unsupported eventType: " + "T".repeat(600)).substring(0, 500)), eq(NOW));
    }

    @Test
    void deadLetterPayloadIsLoggedOnlyWhenEnabled_andTruncatedTo2000() {
        properties.setDeadLetterLogPayload(true);
        claims(message("UNKNOWN_SHORT", 0, "{\"secret\":false}"), message("UNKNOWN_LONG", 0, "p".repeat(2500)));

        processor().processOnce();

        assertThat(logged())
                .contains("payload={\"secret\":false}")
                .contains("payload=" + "p".repeat(2000) + "...")
                .doesNotContain("p".repeat(2001));
    }

    @Test
    void claimAndEachStatusChange_runInTheirOwnTransactions() {
        claims(message("OK", 0, "{}"), message("OK", 0, "{}"));

        processor(handler("OK")).processOnce();

        // 1 transaction pour le claim + 1 par message traité.
        verify(transactionManager, times(3)).getTransaction(any());
        verify(transactionManager, times(3)).commit(any());
        verify(transactionManager, never()).rollback(any());
    }
}
