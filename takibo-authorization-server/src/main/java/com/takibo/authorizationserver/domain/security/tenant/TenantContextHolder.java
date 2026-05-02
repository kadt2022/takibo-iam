package com.takibo.authorizationserver.domain.security.tenant;

/**
 * Thread-local holder for tenant context.
 * Must be cleared after each request to prevent memory leaks.
 * 
 * Usage:
 * <pre>
 * TenantContext context = new TenantContext(orgId, spaceId);
 * TenantContextHolder.set(context);
 * try {
 *     // Use tenant context
 *     TenantContext ctx = TenantContextHolder.getOrThrow();
 * } finally {
 *     TenantContextHolder.clear();
 * }
 * </pre>
 */
public final class TenantContextHolder {
    
    private static final ThreadLocal<TenantContext> CONTEXT = new ThreadLocal<>();
    
    private TenantContextHolder() {
        // Utility class - no instantiation
    }
    
    /**
     * Set tenant context for current thread.
     * 
     * @param context Tenant context to set
     */
    public static void set(TenantContext context) {
        CONTEXT.set(context);
    }
    
    /**
     * Get tenant context for current thread.
     * 
     * @return Tenant context, or null if not set
     */
    public static TenantContext get() {
        return CONTEXT.get();
    }
    
    /**
     * Get tenant context for current thread, throwing exception if not set.
     * 
     * @return Tenant context (never null)
     * @throws IllegalStateException if tenant context not set
     */
    public static TenantContext getOrThrow() {
        TenantContext context = get();
        if (context == null) {
            throw new IllegalStateException("Tenant context not set");
        }
        return context;
    }
    
    /**
     * Clear tenant context for current thread.
     * Must be called after each request to prevent memory leaks.
     */
    public static void clear() {
        CONTEXT.remove();
    }
}
