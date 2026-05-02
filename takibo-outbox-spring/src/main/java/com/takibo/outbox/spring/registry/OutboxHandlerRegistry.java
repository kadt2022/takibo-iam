package com.takibo.outbox.spring.registry;

import com.takibo.outbox.core.port.OutboxHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class OutboxHandlerRegistry {

    private static final Logger log = LoggerFactory.getLogger(OutboxHandlerRegistry.class);

    private final Map<String, OutboxHandler> handlersByType;

    public OutboxHandlerRegistry(List<OutboxHandler> handlers) {
        Map<String, OutboxHandler> map = new HashMap<>();
        for (OutboxHandler handler : handlers) {
            String type = normalize(handler.eventType());
            if (map.containsKey(type)) {
                throw new IllegalStateException("Duplicate OutboxHandler for eventType: " + type);
            }
            map.put(type, handler);
        }
        this.handlersByType = Collections.unmodifiableMap(map);

        if (handlersByType.isEmpty()) {
            log.info("Takibo Outbox: no handlers registered");
        } else {
            log.info("Takibo Outbox: handlers registered: {}", handlersByType.keySet());
        }
    }

    public OutboxHandler getOrNull(String eventType) {
        return handlersByType.get(normalize(eventType));
    }

    private String normalize(String eventType) {
        return eventType == null ? "" : eventType.trim();
    }
}
