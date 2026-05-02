package com.takibo.audit.infrastructure.service;

import com.takibo.audit.domain.LogEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class LogDispatcher {
    private final List<LogConsumer> consumers;

    public void dispatch(LogEvent event) {
        consumers.forEach(c -> c.consume(event));
    }
}