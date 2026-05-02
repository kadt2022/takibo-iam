package com.takibo.adp.spring.adapter;

import lombok.extern.slf4j.Slf4j;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

@Slf4j
public class RequestVelocityTracker {
    
    private final Map<String, SubjectVelocity> velocities = new ConcurrentHashMap<>();
    
    public void recordRequest(String subjectId) {
        if (subjectId == null) {
            return;
        }
        
        velocities.computeIfAbsent(subjectId, k -> new SubjectVelocity())
            .recordRequest(Instant.now());
    }
    
    public VelocitySnapshot getVelocity(String subjectId) {
        if (subjectId == null) {
            return new VelocitySnapshot(null, null);
        }
        
        SubjectVelocity velocity = velocities.get(subjectId);
        if (velocity == null) {
            return new VelocitySnapshot(null, null);
        }
        
        return velocity.getSnapshot(Instant.now());
    }
    
    public record VelocitySnapshot(Integer last10s, Integer last60s) {}
    
    private static class SubjectVelocity {
        private final CopyOnWriteArrayList<Instant> requests = new CopyOnWriteArrayList<>();
        
        void recordRequest(Instant timestamp) {
            requests.add(timestamp);
            
            if (requests.size() > 1000) {
                cleanup(timestamp);
            }
        }
        
        VelocitySnapshot getSnapshot(Instant now) {
            cleanup(now);
            
            Instant threshold10s = now.minusSeconds(10);
            Instant threshold60s = now.minusSeconds(60);
            
            int count10s = 0;
            int count60s = 0;
            
            for (Instant req : requests) {
                if (req.isAfter(threshold10s)) {
                    count10s++;
                    count60s++;
                } else if (req.isAfter(threshold60s)) {
                    count60s++;
                }
            }
            
            return new VelocitySnapshot(count10s, count60s);
        }
        
        void cleanup(Instant now) {
            Instant cutoff = now.minusSeconds(90);
            requests.removeIf(req -> req.isBefore(cutoff));
        }
    }
}
