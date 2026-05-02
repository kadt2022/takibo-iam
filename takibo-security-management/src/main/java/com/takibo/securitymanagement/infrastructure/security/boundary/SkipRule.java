package com.takibo.securitymanagement.infrastructure.security.boundary;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.server.PathContainer;
import org.springframework.web.util.pattern.PathPattern;

public record SkipRule(PathPattern pattern, String httpMethod) {
        boolean matches(HttpServletRequest req, PathContainer path) {
            if (httpMethod != null && !httpMethod.equalsIgnoreCase(req.getMethod())) return false;
            return pattern.matches(path);
        }
    }