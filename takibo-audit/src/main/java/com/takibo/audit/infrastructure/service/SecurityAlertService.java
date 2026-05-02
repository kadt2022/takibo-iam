package com.takibo.audit.infrastructure.service;


import com.takibo.audit.infrastructure.entity.AuditEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class SecurityAlertService {
    private final AlertEmailService emailService;
    private final AlertLogService logService;

    @Async
    public void triggerAlert(AuditEvent event) {
        logService.logEvent(event);
        emailService.sendAlert(event);
    }
}