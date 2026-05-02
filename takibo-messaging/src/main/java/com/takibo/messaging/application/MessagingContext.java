package com.takibo.messaging.application;


import com.takibo.outbox.core.model.OutboxEnvelope;
import lombok.Builder;

import java.util.UUID;

@Builder
public final class MessagingContext {

    private final UUID correlationOutboxId;
    private final String traceId;

    private MessagingContext(UUID correlationOutboxId, String traceId) {
        this.correlationOutboxId = correlationOutboxId;
        this.traceId = traceId;
    }

    public static MessagingContext none() {
        return new MessagingContext(null, null);
    }

    public static MessagingContext fromOutbox(OutboxEnvelope envelope) {
        if (envelope == null) {
            return none();
        }
        return new MessagingContext(envelope.id(), null);
    }

    public UUID correlationOutboxId() {
        return correlationOutboxId;
    }

    public String traceId() {
        return traceId;
    }

    public MessagingContext withTraceId(String traceId) {
        return new MessagingContext(correlationOutboxId, traceId);
    }
}
