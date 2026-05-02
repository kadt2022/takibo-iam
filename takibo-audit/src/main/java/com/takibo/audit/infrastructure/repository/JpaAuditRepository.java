package com.takibo.audit.infrastructure.repository;

import com.takibo.audit.infrastructure.entity.AuditEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface JpaAuditRepository extends JpaRepository<AuditEvent, Long> {
    List<AuditEvent> findByEntityTypeAndEntityId(String entityType, String entityId);
}