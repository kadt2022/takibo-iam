package com.takibo.audit.infrastructure.service;


import com.takibo.audit.domain.LogEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class ConsoleLogConsumer implements LogConsumer {
  
    @Override
    public void consume(LogEvent event) {
        log.info("[{}] {} - Account: {}, User: {}, Params: {}",
                event.getLevel(),
                event.getAction(),
                event.getActorAccountId(),
                event.getActorUserId(),
                event.getParams());
    }
}
