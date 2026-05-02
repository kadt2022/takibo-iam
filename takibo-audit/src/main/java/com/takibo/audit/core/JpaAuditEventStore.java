package com.takibo.audit.core;

import com.takibo.audit.api.AuditEventStore;
import com.takibo.audit.infrastructure.entity.AuditEvent;
import com.takibo.audit.infrastructure.repository.JpaAuditRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class JpaAuditEventStore implements AuditEventStore {
    private final JpaAuditRepository repository;

    public JpaAuditEventStore(JpaAuditRepository repository) {
        this.repository = repository;
    }

    @Override
    public String getName() { return "jpa"; }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void save(AuditEvent event) {
        repository.saveAndFlush(event);
    }
}
