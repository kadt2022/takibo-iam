package com.takibo.audit.infrastructure.config;


import com.takibo.audit.infrastructure.entity.AuditEvent;
import com.takibo.audit.domain.AuditType;
import com.takibo.audit.infrastructure.service.SecurityAlertService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class SecurityAuditStrategy implements AuditLogStrategy {
    private final SecurityAlertService alertService;

    @Override
    public boolean supports(AuditType auditType) {
        return auditType == AuditType.LOGIN_FAILED || auditType == AuditType.SECURITY;
    }

    @Override
    public void log(AuditEvent event) {
        if ("FAILED".equals(event.getStatus())) {
            alertService.triggerAlert(event);
        }
    }
}