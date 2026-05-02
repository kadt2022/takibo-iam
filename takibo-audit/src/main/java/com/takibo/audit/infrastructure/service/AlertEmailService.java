package com.takibo.audit.infrastructure.service;


import com.takibo.audit.infrastructure.entity.AuditEvent;
import com.takibo.messaging.application.MessagingContext;
import com.takibo.messaging.application.MessagingDispatcher;
import com.takibo.messaging.domain.MessageAction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class AlertEmailService {
    private static final Logger log = LoggerFactory.getLogger(AlertEmailService.class);

    private final ObjectProvider<MessagingDispatcher> dispatcherProvider;

    public AlertEmailService(ObjectProvider<MessagingDispatcher> dispatcherProvider) {
        this.dispatcherProvider = dispatcherProvider;
    }

    public void sendAlert(AuditEvent event) {
        MessagingDispatcher dispatcher = dispatcherProvider.getIfAvailable();
        if (dispatcher == null) {
            log.warn("Audit alert email skipped: takibo-messaging dispatcher is not available");
            return;
        }

        MessageAction action = MessageAction.builder("AUDIT_SECURITY_ALERT")
                .orgId(parseUuid(event.getOrgId()))
                .spaceId(parseUuid(event.getSpaceId()))
                .dedupKey("MSG:AUDIT_SECURITY_ALERT:" + event.getId())
                .attribute("auditType", event.getAuditType())
                .attribute("entityType", event.getEntityType())
                .attribute("entityId", event.getEntityId())
                .attribute("userId", event.getUserId())
                .attribute("clientIp", event.getClientIp())
                .attribute("traceId", event.getTraceId())
                .build();

        dispatcher.dispatch(action, MessagingContext.none().withTraceId(event.getTraceId()));
    }

    private UUID parseUuid(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }
}
