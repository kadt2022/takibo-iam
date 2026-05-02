package com.takibo.authorizationserver.infrastructure.jpa.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.net.InetAddress;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(
        name = "tas_audit_events",
        indexes = {
                @Index(name = "idx_tae_org", columnList = "org_id"),
                @Index(name = "idx_tae_org_space", columnList = "org_id, space_id"),
                @Index(name = "idx_tae_org_time", columnList = "org_id, occurred_at"),
                @Index(name = "idx_tae_org_account_time", columnList = "org_id, account_id, occurred_at"),
                @Index(name = "idx_tae_event_type_time", columnList = "event_type, occurred_at"),
                @Index(name = "idx_tae_org_client_time", columnList = "org_id, client_id, occurred_at"),
                @Index(name = "idx_tae_org_status_time", columnList = "org_id, status, occurred_at")
        }
)
public class TasAuditEventEntity {

    @Id
    @Column(name = "event_id", nullable = false)
    private UUID eventId;

    @Column(name = "org_id", nullable = false)
    private UUID orgId;

    @Column(name = "space_id")
    private UUID spaceId;

    @Column(name = "event_type", nullable = false, length = 64)
    private String eventType;

    @Column(name = "status", length = 32)
    private String status;

    @Column(name = "account_id")
    private UUID accountId;

    @Column(name = "client_id", length = 128)
    private String clientId;

    @Column(name = "occurred_at", nullable = false)
    private OffsetDateTime occurredAt;

    @JdbcTypeCode(SqlTypes.INET)
    @Column(name = "ip_address", columnDefinition = "inet")
    private InetAddress ipAddress;

    @Column(name = "user_agent", length = 4000)
    private String userAgent;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata_json", columnDefinition = "jsonb")
    private Map<String, Object> metadataJson;
}
