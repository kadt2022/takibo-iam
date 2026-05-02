package com.takibo.audit.infrastructure.service;

import com.takibo.audit.infrastructure.entity.AuditEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class AlertLogService {

    private static final String ALERT_TEMPLATE = """
        🛡️ SECURITY ALERT [{}] 🛡️
        --------------------------------
        Type: {}
        Gravity: {}
        Entity: {} (ID: {})
        User: {}
        IP: {}
        TraceID: {}
        Timestamp: {}
        Details: {}
        --------------------------------
        """;

    public void logEvent(AuditEvent event) {
        // Utilisez log.warn() normalement - Lombok fournit déjà l'instance 'log'
        log.warn(ALERT_TEMPLATE,
                event.getStatus(),
                event.getAuditType(),
                event.getEntityType(),
                event.getEntityId(),
                event.getUserId(),
                event.getClientIp(),
                event.getTraceId(),
                event.getTimestamp(),
                event.getError() != null ? event.getError() : "N/A"
        );
    }
}