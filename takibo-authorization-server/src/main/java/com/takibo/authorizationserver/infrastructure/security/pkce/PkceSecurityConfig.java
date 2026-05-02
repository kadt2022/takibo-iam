package com.takibo.authorizationserver.infrastructure.security.pkce;

import com.takibo.authorizationserver.infrastructure.jpa.repository.OAuth2ClientLookupRepository;
import com.takibo.authorizationserver.infrastructure.security.error.OAuth2HttpErrorWriter;
import com.takibo.authorizationserver.infrastructure.security.tenant.ClientIdExtractor;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

@Configuration
public class PkceSecurityConfig {

    @Bean
    public FilterRegistrationBean<PkceEnforcementFilter> pkceEnforcementFilter(
            OAuth2ClientLookupRepository repository,
            ClientIdExtractor clientIdExtractor,
            OAuth2HttpErrorWriter oAuth2HttpErrorWriter
    ) {
        PkceEnforcementFilter filter = new PkceEnforcementFilter(
                repository,
                clientIdExtractor,
                oAuth2HttpErrorWriter
        );

        FilterRegistrationBean<PkceEnforcementFilter> registration = new FilterRegistrationBean<>(filter);
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 20);
        registration.addUrlPatterns("/oauth2/*");
        return registration;
    }
}
