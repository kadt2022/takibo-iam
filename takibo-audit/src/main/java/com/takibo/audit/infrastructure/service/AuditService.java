package com.takibo.audit.infrastructure.service;

import com.takibo.audit.api.AuditEventStore;
import com.takibo.audit.infrastructure.entity.AuditEvent;
import com.takibo.audit.infrastructure.config.AuditLogStrategy;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class AuditService {
    private final @Qualifier("auditStoreRouter") AuditEventStore store;
    private final List<AuditLogStrategy> strategies;

    public void save(AuditEvent event) {
        try {
            store.save(event);
        } catch (Exception e) {
            // Filet de sécurité : ne jamais “perdre” un audit si JPA tombe
            System.err.println("[AUDIT-FALLBACK] " + String.valueOf(event));
        }
        strategies.stream()
                .filter(s -> s.supports(event.getAuditType()))
                .forEach(s -> s.log(event));
    }
}