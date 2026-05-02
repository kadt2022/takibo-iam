package com.takibo.audit.logging;

import com.takibo.audit.infrastructure.config.AuditContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class AuditContextInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        // Initialize AuditContext from MDC (which was populated by TraceIdInterceptor)
        AuditContext.init();
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        // Clear the AuditContext at the end of the request
        AuditContext.clear();
    }
}