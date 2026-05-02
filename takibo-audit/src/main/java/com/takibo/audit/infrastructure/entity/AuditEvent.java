package com.takibo.audit.infrastructure.entity;

import com.takibo.audit.domain.AuditType;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Map;

@Entity
@Table(name = "audit_event")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuditEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Instant timestamp;

    @Enumerated(EnumType.STRING)
    @Column(length = 50)
    private AuditType auditType;

    @Column(length = 100)
    private String entityType;

    @Column(length = 500)
    private String entityId;

    @Column(length = 100)
    private String actorAccountId;

    @Column(length = 100)
    private String userId;

    @Column(length = 100)
    private String actorUserId;

    @Column(length = 100)
    private String orgId;

    @Column(length = 100)
    private String spaceId;

    @Column(length = 50)
    private String actorType;

    @Column(length = 100)
    private String actorSource;

    @Column(length = 255)
    private String action;

    @Column(length = 20)
    private String status;

    @Column(columnDefinition = "TEXT")
    private String error;

    @Column(columnDefinition = "TEXT")
    private String details;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false)
    private Map<String, Object> params;

    @Column(length = 50)
    private String traceId;

    @Column(length = 50)
    private String clientIp;

    @Column(length = 255)
    private String userAgent;

    @Column(length = 100)
    private String clientApp;

    private Long durationMs;

    @Column(length = 50)
    private String httpMethod;

    @Column(length = 500)
    private String endpoint;
}
