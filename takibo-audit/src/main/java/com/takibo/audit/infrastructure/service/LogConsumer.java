package com.takibo.audit.infrastructure.service;


import com.takibo.audit.domain.LogEvent;

public interface LogConsumer {
    void consume(LogEvent event);
}