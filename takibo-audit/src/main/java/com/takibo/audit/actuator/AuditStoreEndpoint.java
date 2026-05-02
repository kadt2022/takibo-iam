package com.takibo.audit.actuator;

import com.takibo.audit.core.SmartAuditStoreRouter;
import org.springframework.boot.actuate.endpoint.annotation.*;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@Endpoint(id = "auditstores")
public class AuditStoreEndpoint {

    private final SmartAuditStoreRouter router;

    public AuditStoreEndpoint(SmartAuditStoreRouter router) {
        this.router = router;
    }

    @ReadOperation
    public Map<String, Object> status() {
        return Map.of(
                "currentMode", router.getMode().name(),
                "stores", router.getStores().stream()
                        .map(s -> Map.of(
                                "name", s.getName(),
                                "status", s.healthCheck().status()
                        )).toList()
        );
    }

    @WriteOperation
    public void changeMode(@Selector String mode) {
        router.setMode(SmartAuditStoreRouter.Mode.valueOf(mode.toUpperCase()));
    }
}
