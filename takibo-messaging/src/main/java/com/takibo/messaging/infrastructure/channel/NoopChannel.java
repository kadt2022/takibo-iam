package com.takibo.messaging.infrastructure.channel;

import com.takibo.messaging.application.channel.MessageChannel;
import com.takibo.messaging.domain.ChannelType;
import com.takibo.messaging.domain.DeliveryStatus;
import com.takibo.messaging.infrastructure.jpa.MessageDeliveryEntity;

public class NoopChannel implements MessageChannel {

    private final ChannelType channelType;

    public NoopChannel(ChannelType channelType) {
        this.channelType = channelType;
    }

    @Override
    public ChannelType channelType() {
        return channelType;
    }

    @Override
    public ChannelResult send(MessageDeliveryEntity delivery) {
        return new ChannelResult(DeliveryStatus.SENT, null);
    }
}
