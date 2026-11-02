package com.takibo.messaging.application;

import com.takibo.messaging.application.channel.MessageChannel;
import com.takibo.messaging.domain.ChannelType;
import com.takibo.messaging.domain.DeliveryStatus;
import com.takibo.messaging.infrastructure.jpa.MessageDeliveryEntity;
import com.takibo.messaging.testsupport.FakeMessageDeliveryRepository;
import com.takibo.messaging.testsupport.FakeMessageDeliveryRepository.AttemptUpdate;
import com.takibo.messaging.testsupport.FakeMessageDeliveryRepository.ClaimCall;
import com.takibo.messaging.testsupport.FakeMessageDeliveryRepository.StatusUpdate;
import com.takibo.messaging.testsupport.RecordingTransactionManager;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DeliveryProcessorTest {

    private static final Instant NOW = Instant.parse("2026-01-06T10:00:00Z");
    private static final Duration LOCK_TIMEOUT = Duration.ofSeconds(30);
    private static final int MAX_ATTEMPTS = 3;

    private final FakeMessageDeliveryRepository fakeRepository = new FakeMessageDeliveryRepository();
    private final RecordingTransactionManager transactionManager = new RecordingTransactionManager();
    private final SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
    private final ScriptedChannel emailChannel = new ScriptedChannel();
    private final Map<ChannelType, MessageChannel> channels = new EnumMap<>(Map.of(ChannelType.EMAIL, emailChannel));

    private final DeliveryProcessor processor = new DeliveryProcessor(
            fakeRepository.repository(),
            channels,
            attempt -> Duration.ofMinutes(attempt),
            new DeliveryProcessor.DeliveryProcessorSettings(25, MAX_ATTEMPTS, LOCK_TIMEOUT, "node-1"),
            Clock.fixed(NOW, ZoneOffset.UTC),
            transactionManager,
            meterRegistry
    );

    // --- Eligibilite et reservation ---

    @Test
    void hasRunnableQueriesWithNowAndStaleLockThreshold() {
        fakeRepository.eligible = true;

        assertTrue(processor.hasRunnable());
        assertEquals(List.of(new FakeMessageDeliveryRepository.EligibleCall(NOW, NOW.minus(LOCK_TIMEOUT))),
                fakeRepository.eligibleCalls);
    }

    @Test
    void hasRunnableIsFalseWhenNothingEligible() {
        assertFalse(processor.hasRunnable());
    }

    @Test
    void claimsBatchInsideTransactionWithConfiguredSettings() {
        assertEquals(0, processor.processBatch());

        assertEquals(List.of(new ClaimCall(NOW, NOW.minus(LOCK_TIMEOUT), "node-1", 25)), fakeRepository.claimCalls);
        assertEquals(1, transactionManager.begun);
        assertEquals(1, transactionManager.committed);
    }

    @Test
    void returnsZeroWhenClaimReturnsNull() {
        fakeRepository.claimResult = null;

        assertEquals(0, processor.processBatch());
        assertTrue(emailChannel.sent.isEmpty());
    }

    @Test
    void processesEveryClaimedDeliveryAndReturnsCount() {
        MessageDeliveryEntity first = delivery("EMAIL", 0);
        MessageDeliveryEntity second = delivery("EMAIL", 0);
        fakeRepository.claimResult = List.of(first, second);

        assertEquals(2, processor.processBatch());

        assertEquals(List.of(first, second), emailChannel.sent);
        assertEquals(2, fakeRepository.statusUpdates.size());
    }

    // --- Succes ---

    @Test
    void marksSentDeliveryAsSentAndCountsIt() {
        MessageDeliveryEntity delivery = delivery("EMAIL", 0);
        fakeRepository.claimResult = List.of(delivery);

        processor.processBatch();

        assertEquals(List.of(new StatusUpdate(delivery.getId(), DeliveryStatus.SENT, NOW)), fakeRepository.statusUpdates);
        assertTrue(fakeRepository.attemptUpdates.isEmpty());
        assertEquals(1.0, counter("takibo_messaging_delivery_sent"));
        assertEquals(2, transactionManager.committed, "reservation puis mise a jour, chacune dans sa transaction");
    }

    // --- Echec transitoire ---

    @Test
    void schedulesRetryWithBackoffOnTransientFailure() {
        MessageDeliveryEntity delivery = delivery("EMAIL", 1);
        fakeRepository.claimResult = List.of(delivery);
        emailChannel.result = MessageChannel.ChannelResult.failed("smtp timeout");

        processor.processBatch();

        assertEquals(List.of(new AttemptUpdate(delivery.getId(), DeliveryStatus.FAILED, 2,
                NOW.plus(Duration.ofMinutes(2)), "smtp timeout", NOW)), fakeRepository.attemptUpdates);
        assertTrue(fakeRepository.statusUpdates.isEmpty());
        assertEquals(1.0, counter("takibo_messaging_delivery_failed"));
    }

    @Test
    void usesGenericErrorWhenChannelGivesNoReason() {
        MessageDeliveryEntity delivery = delivery("EMAIL", 0);
        fakeRepository.claimResult = List.of(delivery);
        emailChannel.result = MessageChannel.ChannelResult.failed(null);

        processor.processBatch();

        AttemptUpdate update = fakeRepository.attemptUpdates.get(0);
        assertEquals(DeliveryStatus.FAILED, update.status());
        assertEquals("delivery failed", update.lastError());
        assertEquals(1, update.attempts());
    }

    // --- Echec definitif ---

    @Test
    void marksDeadWhenMaxAttemptsReached() {
        MessageDeliveryEntity delivery = delivery("EMAIL", MAX_ATTEMPTS - 1);
        fakeRepository.claimResult = List.of(delivery);
        emailChannel.result = MessageChannel.ChannelResult.failed("mailbox unavailable");

        processor.processBatch();

        assertEquals(List.of(new AttemptUpdate(delivery.getId(), DeliveryStatus.DEAD, MAX_ATTEMPTS, NOW,
                "mailbox unavailable", NOW)), fakeRepository.attemptUpdates);
        assertEquals(1.0, counter("takibo_messaging_delivery_dead"));
        assertNull(meterRegistry.find("takibo_messaging_delivery_failed").counter());
    }

    @Test
    void marksDeadImmediatelyWhenChannelReportsPermanentFailure() {
        MessageDeliveryEntity delivery = delivery("EMAIL", 0);
        fakeRepository.claimResult = List.of(delivery);
        emailChannel.result = MessageChannel.ChannelResult.dead("invalid address");

        processor.processBatch();

        AttemptUpdate update = fakeRepository.attemptUpdates.get(0);
        assertEquals(DeliveryStatus.DEAD, update.status());
        assertEquals(1, update.attempts());
        assertEquals("invalid address", update.lastError());
    }

    @Test
    void usesGenericErrorWhenDeadWithoutReason() {
        fakeRepository.claimResult = List.of(delivery("EMAIL", 0));
        emailChannel.result = MessageChannel.ChannelResult.dead(null);

        processor.processBatch();

        assertEquals("delivery failed", fakeRepository.attemptUpdates.get(0).lastError());
    }

    @Test
    void truncatesDeadErrorTo500Characters() {
        fakeRepository.claimResult = List.of(delivery("EMAIL", 0));
        emailChannel.result = MessageChannel.ChannelResult.dead("x".repeat(800));

        processor.processBatch();

        assertEquals("x".repeat(500), fakeRepository.attemptUpdates.get(0).lastError());
    }

    @Test
    void marksDeadWithoutSendingWhenChannelNameIsUnknown() {
        MessageDeliveryEntity delivery = delivery("CARRIER_PIGEON", 0);
        fakeRepository.claimResult = List.of(delivery);

        processor.processBatch();

        assertTrue(emailChannel.sent.isEmpty());
        assertEquals(List.of(new AttemptUpdate(delivery.getId(), DeliveryStatus.DEAD, 1, NOW,
                "Unsupported channel: CARRIER_PIGEON", NOW)), fakeRepository.attemptUpdates);
    }

    @Test
    void marksDeadWhenChannelNameIsMissing() {
        fakeRepository.claimResult = List.of(delivery(null, 0));

        processor.processBatch();

        assertEquals("Unsupported channel: null", fakeRepository.attemptUpdates.get(0).lastError());
        assertEquals(DeliveryStatus.DEAD, fakeRepository.attemptUpdates.get(0).status());
    }

    @Test
    void marksDeadWhenNoChannelIsRegisteredForType() {
        fakeRepository.claimResult = List.of(delivery("WEBHOOK", 2));

        processor.processBatch();

        AttemptUpdate update = fakeRepository.attemptUpdates.get(0);
        assertEquals(DeliveryStatus.DEAD, update.status());
        assertEquals(3, update.attempts());
        assertEquals("No channel registered for: WEBHOOK", update.lastError());
    }

    @Test
    void oneDeadDeliveryDoesNotPreventProcessingOfTheNextOne() {
        MessageDeliveryEntity unsupported = delivery("UNKNOWN", 0);
        MessageDeliveryEntity valid = delivery("EMAIL", 0);
        fakeRepository.claimResult = List.of(unsupported, valid);

        assertEquals(2, processor.processBatch());

        assertEquals(DeliveryStatus.DEAD, fakeRepository.attemptUpdates.get(0).status());
        assertEquals(List.of(new StatusUpdate(valid.getId(), DeliveryStatus.SENT, NOW)), fakeRepository.statusUpdates);
    }

    // --- Construction ---

    @Test
    void rejectsMissingCollaborators() {
        var settings = new DeliveryProcessor.DeliveryProcessorSettings(1, 1, LOCK_TIMEOUT, "n");
        var repo = fakeRepository.repository();
        Clock clock = Clock.systemUTC();

        assertThrows(NullPointerException.class,
                () -> new DeliveryProcessor(null, channels, a -> Duration.ZERO, settings, clock, transactionManager, meterRegistry));
        assertThrows(NullPointerException.class,
                () -> new DeliveryProcessor(repo, null, a -> Duration.ZERO, settings, clock, transactionManager, meterRegistry));
        assertThrows(NullPointerException.class,
                () -> new DeliveryProcessor(repo, channels, null, settings, clock, transactionManager, meterRegistry));
        assertThrows(NullPointerException.class,
                () -> new DeliveryProcessor(repo, channels, a -> Duration.ZERO, null, clock, transactionManager, meterRegistry));
        assertThrows(NullPointerException.class,
                () -> new DeliveryProcessor(repo, channels, a -> Duration.ZERO, settings, null, transactionManager, meterRegistry));
        assertThrows(NullPointerException.class,
                () -> new DeliveryProcessor(repo, channels, a -> Duration.ZERO, settings, clock, null, meterRegistry));
        assertThrows(NullPointerException.class,
                () -> new DeliveryProcessor(repo, channels, a -> Duration.ZERO, settings, clock, transactionManager, null));
    }

    // --- Helpers ---

    private double counter(String name) {
        Counter counter = meterRegistry.find(name).tag("channel", "EMAIL").counter();
        return counter == null ? 0.0 : counter.count();
    }

    private static MessageDeliveryEntity delivery(String channel, int attempts) {
        MessageDeliveryEntity e = new MessageDeliveryEntity();
        e.setId(UUID.randomUUID());
        e.setMessageType("WELCOME_SPACE");
        e.setChannel(channel);
        e.setRecipientValue("a@takibo.test");
        e.setStatus(DeliveryStatus.PROCESSING);
        e.setAttempts(attempts);
        return e;
    }

    private static final class ScriptedChannel implements MessageChannel {
        private final List<MessageDeliveryEntity> sent = new ArrayList<>();
        private ChannelResult result = ChannelResult.sent();

        @Override
        public ChannelType channelType() {
            return ChannelType.EMAIL;
        }

        @Override
        public ChannelResult send(MessageDeliveryEntity delivery) {
            sent.add(delivery);
            return result;
        }
    }
}
