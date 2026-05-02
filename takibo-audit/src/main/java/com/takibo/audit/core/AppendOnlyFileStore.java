package com.takibo.audit.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.takibo.audit.api.AuditEventStore;
import com.takibo.audit.api.AuditStoreException;
import com.takibo.audit.infrastructure.entity.AuditEvent;

import java.io.IOException;
import java.nio.file.*;
import java.util.concurrent.locks.ReentrantLock;

public class AppendOnlyFileStore implements AuditEventStore {
    private final Path filePath;
    private final ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());
    private final ReentrantLock lock = new ReentrantLock();

    public AppendOnlyFileStore(String filePath) {
        this.filePath = Path.of(filePath);
    }

    @Override
    public String getName() { return "file"; }

    @Override
    public void save(AuditEvent event) {
        lock.lock();
        try {
            String json = mapper.writeValueAsString(event) + System.lineSeparator();
            Files.writeString(filePath, json, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            throw new AuditStoreException("File write failed", e);
        } finally {
            lock.unlock();
        }
    }
}
