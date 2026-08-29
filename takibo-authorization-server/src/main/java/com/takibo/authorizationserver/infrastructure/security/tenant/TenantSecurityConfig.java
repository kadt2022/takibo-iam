package com.takibo.authorizationserver.infrastructure.security.tenant;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.takibo.authorizationserver.domain.client.ResolvedOAuthClientResolver;
import com.takibo.authorizationserver.infrastructure.security.error.OAuth2HttpErrorWriter;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

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
            ResolvedOAuthClientResolver resolvedOAuthClientResolver,
            ClientIdExtractor clientIdExtractor,
            OAuth2HttpErrorWriter oAuth2HttpErrorWriter) {

        TenantResolutionFilter filter =
                new TenantResolutionFilter(resolvedOAuthClientResolver, clientIdExtractor, oAuth2HttpErrorWriter);

        FilterRegistrationBean<TenantResolutionFilter> registration = new FilterRegistrationBean<>(filter);
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 10); // Run early, before Spring Security
        registration.addUrlPatterns("/oauth2/*", "/.well-known/*", "/userinfo", "/userinfo/*");

        return registration;
    }
}
