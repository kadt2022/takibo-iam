package com.takibo.messaging.application;

import com.takibo.messaging.application.channel.MessageChannel;
import com.takibo.messaging.domain.ChannelType;
import com.takibo.messaging.domain.DeliveryStatus;
import com.takibo.messaging.infrastructure.channel.NoopChannel;
import com.takibo.messaging.infrastructure.jpa.MessageDeliveryEntity;
import com.takibo.messaging.testsupport.FakeMessageDeliveryRepository;
import com.takibo.messaging.testsupport.RecordingTransactionManager;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DeliverySchedulerTest {

    private final FakeMessageDeliveryRepository fakeRepository = new FakeMessageDeliveryRepository();
    private final DeliveryScheduler scheduler = new DeliveryScheduler(new DeliveryProcessor(
            fakeRepository.repository(),
            Map.<ChannelType, MessageChannel>of(ChannelType.EMAIL, new NoopChannel(ChannelType.EMAIL)),
            attempt -> Duration.ofSeconds(1),
            new DeliveryProcessor.DeliveryProcessorSettings(10, 3, Duration.ofSeconds(30), "node"),
            Clock.systemUTC(),
            new RecordingTransactionManager(),
            new SimpleMeterRegistry()
    ));

    @Test
    void doesNotClaimAnythingWhenNoDeliveryIsRunnable() {
        fakeRepository.eligible = false;

        scheduler.tick();

        assertEquals(1, fakeRepository.eligibleCalls.size());
        assertTrue(fakeRepository.claimCalls.isEmpty(), "aucune reservation (et donc aucun verrou) sans travail eligible");
    }

    @Test
    void processesBatchWhenDeliveriesAreRunnable() {
        fakeRepository.eligible = true;
        MessageDeliveryEntity delivery = new MessageDeliveryEntity();
        delivery.setId(UUID.randomUUID());
        delivery.setChannel("EMAIL");
        fakeRepository.claimResult = List.of(delivery);

        scheduler.tick();

        assertEquals(1, fakeRepository.claimCalls.size());
        assertEquals(DeliveryStatus.SENT, fakeRepository.statusUpdates.get(0).status());
    }
}
