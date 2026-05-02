package com.takibo.outbox.core.port;

import com.takibo.outbox.core.model.OutboxEnvelope;

public interface OutboxPublisher {
    void publish(OutboxEnvelope envelope);
}
