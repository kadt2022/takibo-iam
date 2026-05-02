package com.takibo.audit.infrastructure.service;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

@Service
public class FailureCounterService {
    private final Map<String, AtomicInteger> counters = new ConcurrentHashMap<>();

    // Renommez la méthode pour correspondre à l'aspect
    public boolean reachedThreshold(String key, int threshold) {
        AtomicInteger counter = counters.computeIfAbsent(key, k -> new AtomicInteger(0));
        return counter.incrementAndGet() >= threshold;
    }

    @Scheduled(fixedRate = 1, timeUnit = TimeUnit.HOURS)
    public void resetCounters() {
        counters.clear();
    }
}