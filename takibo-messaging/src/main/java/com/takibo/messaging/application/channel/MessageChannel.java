package com.takibo.messaging.application.channel;

import com.takibo.messaging.domain.ChannelType;
import com.takibo.messaging.domain.DeliveryStatus;
import com.takibo.messaging.infrastructure.jpa.MessageDeliveryEntity;

public interface MessageChannel {

    ChannelType channelType();

    ChannelResult send(MessageDeliveryEntity delivery);

    record ChannelResult(DeliveryStatus status, String error) {
        public static ChannelResult sent() {
            return new ChannelResult(DeliveryStatus.SENT, null);
        }

        public static ChannelResult failed(String error) {
            return new ChannelResult(DeliveryStatus.FAILED, error);
        }

        public static ChannelResult dead(String error) {
            return new ChannelResult(DeliveryStatus.DEAD, error);
        }
    }
}
