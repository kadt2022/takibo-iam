package com.takibo.audit.api;


import com.takibo.audit.infrastructure.entity.AuditEvent;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

public interface AuditEventStore {
    String getName();

    void save(AuditEvent event) throws AuditStoreException;

    default CompletableFuture<Void> saveAsync(AuditEvent event) {
        return CompletableFuture.runAsync(() -> {
            try {
                save(event);
            } catch (AuditStoreException e) {
                throw new CompletionException(e);
            }
        });
    }

    default boolean supportsBatching() { return false; }

    default HealthCheckResult healthCheck() { return HealthCheckResult.healthy(); }

    record HealthCheckResult(Status status, String message) {
        public enum Status { HEALTHY, DEGRADED, DOWN}

        public static HealthCheckResult healthy() {
            return new HealthCheckResult(Status.HEALTHY, "OK");
        }
    }
}



