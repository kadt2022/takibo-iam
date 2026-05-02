package com.takibo.adp.test.controller;

import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api")
public class TestController {
    
    @GetMapping("/health")
    public Map<String, Object> health() {
        return Map.of(
            "status", "UP",
            "timestamp", Instant.now()
        );
    }
    
    @GetMapping("/public/info")
    public Map<String, Object> publicInfo() {
        return Map.of(
            "message", "This is a public endpoint",
            "timestamp", Instant.now()
        );
    }
    
    @GetMapping("/dashboard")
    public Map<String, Object> dashboard(Authentication auth) {
        log.info("Dashboard accessed by: {}", auth.getName());
        return Map.of(
            "message", "Welcome to dashboard",
            "user", auth.getName(),
            "timestamp", Instant.now()
        );
    }
    
    @GetMapping("/admin/users")
    public Map<String, Object> adminUsers(Authentication auth) {
        log.info("Admin users endpoint accessed by: {}", auth.getName());
        return Map.of(
            "message", "Admin users list",
            "user", auth.getName(),
            "timestamp", Instant.now()
        );
    }
    
    @PostMapping("/admin/users")
    public Map<String, Object> createUser(Authentication auth, @RequestBody Map<String, Object> userData) {
        log.info("Create user endpoint accessed by: {}", auth.getName());
        return Map.of(
            "message", "User created",
            "user", auth.getName(),
            "timestamp", Instant.now()
        );
    }
    
    @GetMapping("/data/sensitive")
    public Map<String, Object> sensitiveData(Authentication auth) {
        log.info("Sensitive data accessed by: {}", auth.getName());
        return Map.of(
            "message", "Sensitive data",
            "user", auth.getName(),
            "data", "confidential-info",
            "timestamp", Instant.now()
        );
    }
    
    @GetMapping("/profile")
    public Map<String, Object> profile(Authentication auth) {
        return Map.of(
            "username", auth.getName(),
            "authorities", auth.getAuthorities(),
            "timestamp", Instant.now()
        );
    }
}
