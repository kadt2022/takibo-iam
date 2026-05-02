package com.takibo.audit.infrastructure.service;

import com.takibo.audit.core.TakiboAuditUserContextHolder;
import com.takibo.audit.spi.TakiboAuditUserContext;

import java.util.Optional;

@Deprecated
public class SecurityContext {
    private SecurityContext() {
    }

    public static String getCurrentUser() {
        return Optional.ofNullable(TakiboAuditUserContextHolder.getContext())
                .map(TakiboAuditUserContext::getUserId)
                .orElse(null);
    }
}
