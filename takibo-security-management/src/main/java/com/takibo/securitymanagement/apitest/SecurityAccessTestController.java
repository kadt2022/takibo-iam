package com.takibo.securitymanagement.apitest;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

//@Profile({"dev", "test"})
@RestController
@RequestMapping("/debug/secure")
@RequiredArgsConstructor
public class SecurityAccessTestController {

    private final SecurityDebugService securityDebugService;

    @GetMapping("/context")
    public ResponseEntity<?> context(Authentication authentication) {
        return ResponseEntity.ok(securityDebugService.snapshot(authentication, "CONTEXT", null));
    }

    @GetMapping("/user-read")
    @PreAuthorize("hasAuthority('USER_READ')")
    public ResponseEntity<?> userRead(Authentication authentication) {
        return ResponseEntity.ok(securityDebugService.snapshot(authentication, "USER_READ", null));
    }

    @DeleteMapping("/users/{id}")
    @PreAuthorize("hasAuthority('USER_DELETE')")
    public ResponseEntity<?> userDelete(@PathVariable String id, Authentication authentication) {
        return ResponseEntity.ok(securityDebugService.snapshot(authentication, "USER_DELETE", new SecurityDebugService.Extra("targetUserId", id)));
    }

    @GetMapping("/secret")
    @PreAuthorize("hasAuthority('SECRET_READ')")
    public ResponseEntity<?> secretRead(Authentication authentication) {
        return ResponseEntity.ok(securityDebugService.snapshot(authentication, "SECRET_READ", null));
    }

    @GetMapping("/org-admin")
    @PreAuthorize("hasRole('ORG_ADMIN')")
    public ResponseEntity<?> orgAdminOnly(Authentication authentication) {
        return ResponseEntity.ok(securityDebugService.snapshot(authentication, "ROLE_ORG_ADMIN", null));
    }

    @GetMapping("/deny")
    @PreAuthorize("denyAll()")
    public ResponseEntity<?> deny() {
        return ResponseEntity.ok(securityDebugService.snapshot(null, "DENY_ALL", null));
    }

    @GetMapping("/hello")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> hello() {
        return ResponseEntity.ok(Map.of("message", "Bonjour Capitaine Pi de la part du CP"));
    }

    @GetMapping("/hello-org-admin")
    @PreAuthorize("hasRole('ORG_ADMIN')")
    public ResponseEntity<?> helloOrgAdmin() {
        return ResponseEntity.ok(Map.of("message", "Bonjour Capitaine Pi de la part du CP"));
    }
}