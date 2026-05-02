package com.takibo.audit.core;

import com.takibo.audit.spi.TakiboAuditUserContext;

public final class TakiboAuditUserContextHolder {

    private static final ThreadLocal<TakiboAuditUserContext> contextHolder = new ThreadLocal<>();

    private TakiboAuditUserContextHolder() {
    }

    public static void setContext(TakiboAuditUserContext ctx) {
        contextHolder.set(ctx);
    }

    public static TakiboAuditUserContext getContext() {
        return contextHolder.get();
    }

    public static void clear() {
        contextHolder.remove();
    }
}
