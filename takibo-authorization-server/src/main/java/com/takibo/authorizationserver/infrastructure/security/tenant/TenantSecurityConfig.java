package com.takibo.authorizationserver.infrastructure.security.tenant;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.takibo.authorizationserver.domain.security.tenant.TenantResolver;
import com.takibo.authorizationserver.infrastructure.security.error.OAuth2HttpErrorWriter;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.web.servlet.HandlerExceptionResolver;

/**
 * Security configuration for tenant resolution.
 *
 * Registers TenantResolutionFilter early in the filter chain (before Spring Security)
 * to ensure tenant context is available for all downstream security decisions.
 */
@Configuration
public class TenantSecurityConfig {

    @Bean
    public ClientIdExtractor clientIdExtractor() {
        return new ClientIdExtractor();
    }

    @Bean
    public OAuth2HttpErrorWriter oAuth2HttpErrorWriter(ObjectMapper objectMapper) {
        return new OAuth2HttpErrorWriter(objectMapper);
    }

    @Bean
    public FilterRegistrationBean<TenantResolutionFilter> tenantResolutionFilter(
            TenantResolver tenantResolver,
            ClientIdExtractor clientIdExtractor,
            @Qualifier("handlerExceptionResolver") HandlerExceptionResolver handlerExceptionResolver) {

        TenantResolutionFilter filter = new TenantResolutionFilter(tenantResolver, clientIdExtractor, handlerExceptionResolver);

        FilterRegistrationBean<TenantResolutionFilter> registration = new FilterRegistrationBean<>(filter);
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 10); // Run early, before Spring Security
        registration.addUrlPatterns("/oauth2/*", "/.well-known/*", "/userinfo", "/userinfo/*");

        return registration;
    }
}
