package com.takibo.authorizationserver.infrastructure.security.authorize;

import com.takibo.authorizationserver.infrastructure.security.error.OAuth2HttpErrorWriter;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

/**
 * Enregistre {@link AuthorizationCodeClientFilter} entre la résolution du client
 * ({@code TenantResolutionFilter}, +10) et la politique PKCE ({@code PkceEnforcementFilter}, +20).
 */
@Configuration
public class AuthorizationCodeClientSecurityConfig {

    @Bean
    public FilterRegistrationBean<AuthorizationCodeClientFilter> authorizationCodeClientFilter(
            OAuth2HttpErrorWriter oAuth2HttpErrorWriter) {
        FilterRegistrationBean<AuthorizationCodeClientFilter> registration =
                new FilterRegistrationBean<>(new AuthorizationCodeClientFilter(oAuth2HttpErrorWriter));
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 15);
        registration.addUrlPatterns(AuthorizationCodeClientFilter.AUTHORIZATION_ENDPOINT);
        return registration;
    }
}
