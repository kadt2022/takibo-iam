package com.takibo.audit.core;


import com.takibo.audit.api.AuditEventStore;
import com.takibo.audit.infrastructure.entity.AuditEvent;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

public class SmartAuditStoreRouter implements AuditEventStore {
    @Getter
    private final List<AuditEventStore> stores;
    @Getter
    @Setter
    private Mode mode;
    private final MeterRegistry metrics;

    public enum Mode { COMPOSITE, FALLBACK, DIRECT }

    public SmartAuditStoreRouter(List<AuditEventStore> stores, MeterRegistry metrics, Mode mode) {
        this.stores = stores;
        this.metrics = metrics;
        this.mode = mode;
    }

    @Override
    public String getName() { return "router"; }

    @Override
    public void save(AuditEvent event) {
        Timer.Sample timer = Timer.start(metrics);
        try {
            switch (mode) {
                case COMPOSITE -> stores.forEach(s -> safeSave(s, event));
                case FALLBACK  -> stores.stream().filter(s -> safeSave(s, event)).findFirst();
                case DIRECT    -> safeSave(stores.get(0), event);
            }
            timer.stop(metrics.timer("audit.save.success"));
        } catch (Exception e) {
            timer.stop(metrics.timer("audit.save.failed"));
            throw e;
        }
    }

    private boolean safeSave(AuditEventStore store, AuditEvent event) {
        try {
            store.save(event);
            metrics.counter("audit.store.success", "store", store.getName()).increment();
            return true;
        } catch (Exception e) {
            metrics.counter("audit.store.failure", "store", store.getName()).increment();
            return false;
        }
    }
}
