package com.takibo.messaging.infrastructure.channel;

import com.takibo.messaging.application.channel.MessageChannel;
import com.takibo.messaging.domain.ChannelType;
import com.takibo.messaging.domain.DeliveryStatus;
import com.takibo.messaging.infrastructure.jpa.MessageDeliveryEntity;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class NoopChannelTest {

    @Test
    void reportsConfiguredChannelType() {
        assertEquals(ChannelType.IN_APP, new NoopChannel(ChannelType.IN_APP).channelType());
    }

    @Test
    void acknowledgesEveryDeliveryAsSentWithoutError() {
        MessageChannel.ChannelResult result = new NoopChannel(ChannelType.EMAIL).send(new MessageDeliveryEntity());

        assertEquals(DeliveryStatus.SENT, result.status());
        assertNull(result.error());
    }
}
