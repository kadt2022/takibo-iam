package com.takibo.audit.infrastructure.config;


import com.takibo.audit.infrastructure.entity.AuditEvent;
import com.takibo.audit.domain.AuditType;

public interface AuditLogStrategy {
    void log(AuditEvent event);

    default boolean supports(AuditType auditType) {
        return true;
    }
}