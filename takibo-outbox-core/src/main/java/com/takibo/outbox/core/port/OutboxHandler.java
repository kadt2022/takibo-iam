package com.takibo.outbox.core.port;

import com.takibo.outbox.core.model.OutboxEnvelope;

public interface OutboxHandler {
    String eventType();

    void handle(OutboxEnvelope envelope) throws Exception;
}
