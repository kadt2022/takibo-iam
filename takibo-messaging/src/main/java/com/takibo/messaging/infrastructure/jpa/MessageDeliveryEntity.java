package com.takibo.messaging.infrastructure.jpa;

import com.takibo.messaging.domain.DeliveryStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "message_deliveries")
public class MessageDeliveryEntity {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "org_id")
    private UUID orgId;

    @Column(name = "space_id")
    private UUID spaceId;

    @Column(name = "message_type", nullable = false, length = 120)
    private String messageType;

    @Column(name = "channel", nullable = false, length = 30)
    private String channel;

    @Column(name = "recipient_type", nullable = false, length = 30)
    private String recipientType;

    @Column(name = "recipient_value", nullable = false, length = 320)
    private String recipientValue;

    @Column(name = "recipient_key", nullable = false, length = 200)
    private String recipientKey;

    @Column(name = "from_address", length = 320)
    private String fromAddress;

    @Column(name = "subject", length = 255)
    private String subject;

    @Column(name = "body", columnDefinition = "text")
    private String body;

    @Column(name = "payload_json", columnDefinition = "jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
    private String payloadJson;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private DeliveryStatus status;

    @Column(name = "attempts", nullable = false)
    private int attempts;

    @Column(name = "next_run_at", nullable = false)
    private Instant nextRunAt;

    @Column(name = "last_error", length = 500)
    private String lastError;

    @Column(name = "locked_at")
    private Instant lockedAt;

    @Column(name = "locked_by", length = 120)
    private String lockedBy;

    @Column(name = "dedup_key", nullable = false, length = 220)
    private String dedupKey;

    @Column(name = "correlation_outbox_id")
    private UUID correlationOutboxId;

    @Column(name = "trace_id", length = 64)
    private String traceId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public UUID getOrgId() { return orgId; }
    public void setOrgId(UUID orgId) { this.orgId = orgId; }

    public UUID getSpaceId() { return spaceId; }
    public void setSpaceId(UUID spaceId) { this.spaceId = spaceId; }

    public String getMessageType() { return messageType; }
    public void setMessageType(String messageType) { this.messageType = messageType; }

    public String getChannel() { return channel; }
    public void setChannel(String channel) { this.channel = channel; }

    public String getRecipientType() { return recipientType; }
    public void setRecipientType(String recipientType) { this.recipientType = recipientType; }

    public String getRecipientValue() { return recipientValue; }
    public void setRecipientValue(String recipientValue) { this.recipientValue = recipientValue; }

    public String getRecipientKey() { return recipientKey; }
    public void setRecipientKey(String recipientKey) { this.recipientKey = recipientKey; }

    public String getFromAddress() { return fromAddress; }
    public void setFromAddress(String fromAddress) { this.fromAddress = fromAddress; }

    public String getSubject() { return subject; }
    public void setSubject(String subject) { this.subject = subject; }

    public String getBody() { return body; }
    public void setBody(String body) { this.body = body; }

    public String getPayloadJson() { return payloadJson; }
    public void setPayloadJson(String payloadJson) { this.payloadJson = payloadJson; }

    public DeliveryStatus getStatus() { return status; }
    public void setStatus(DeliveryStatus status) { this.status = status; }

    public int getAttempts() { return attempts; }
    public void setAttempts(int attempts) { this.attempts = attempts; }

    public Instant getNextRunAt() { return nextRunAt; }
    public void setNextRunAt(Instant nextRunAt) { this.nextRunAt = nextRunAt; }

    public String getLastError() { return lastError; }
    public void setLastError(String lastError) { this.lastError = lastError; }

    public Instant getLockedAt() { return lockedAt; }
    public void setLockedAt(Instant lockedAt) { this.lockedAt = lockedAt; }

    public String getLockedBy() { return lockedBy; }
    public void setLockedBy(String lockedBy) { this.lockedBy = lockedBy; }

    public String getDedupKey() { return dedupKey; }
    public void setDedupKey(String dedupKey) { this.dedupKey = dedupKey; }

    public UUID getCorrelationOutboxId() { return correlationOutboxId; }
    public void setCorrelationOutboxId(UUID correlationOutboxId) { this.correlationOutboxId = correlationOutboxId; }

    public String getTraceId() { return traceId; }
    public void setTraceId(String traceId) { this.traceId = traceId; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
