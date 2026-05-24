package com.takibo.authorizationserver.infrastructure.springauthserver.token;

import com.takibo.authorizationserver.domain.security.tenant.TenantContext;
import com.takibo.authorizationserver.domain.security.tenant.TenantContextHolder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType;
import org.springframework.security.oauth2.server.authorization.token.JwtEncodingContext;
import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenCustomizer;

@Configuration
public class TakiboOAuth2TokenCustomizer {

    // stub: org_id and space_id are not yet resolved from the real tenant resolver
    @Bean
    public OAuth2TokenCustomizer<JwtEncodingContext> tokenCustomizer() {
        return context -> {
            if (!OAuth2TokenType.ACCESS_TOKEN.equals(context.getTokenType())) {
                return;
            }

            TenantContext tenant = TenantContextHolder.get();
            if (tenant != null) {
                context.getClaims()
                        .claim("org_id", tenant.orgId().toString())
                        .claim("space_id", tenant.spaceId().toString());
            } else {
                context.getClaims()
                        .claim("org_id", "00000000-0000-0000-0000-000000000001")
                        .claim("space_id", "00000000-0000-0000-0000-000000000002");
            }
            context.getClaims().claim("takibo_tenant_source", "stub");
        };
    }
}
