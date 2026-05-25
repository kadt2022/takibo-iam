package com.takibo.iamboot.security;

import com.takibo.identitycore.integration.security.port.CurrentOrganizationContextCase;
import com.takibo.securitycontext.spi.CurrentTakiboSecurityContextProvider;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class TakiboSecurityCurrentOrganizationContextAdapter implements CurrentOrganizationContextCase {

    private final CurrentTakiboSecurityContextProvider contextProvider;

    public TakiboSecurityCurrentOrganizationContextAdapter(CurrentTakiboSecurityContextProvider contextProvider) {
        this.contextProvider = contextProvider;
    }

    @Override
    public UUID requireCurrentOrganizationId() {
        var context = contextProvider.current();

        if (context == null || context.tenant() == null) {
            throw new AccessDeniedException("ORG_CONTEXT_REQUIRED");
        }

        String organizationId = context.tenant().organizationId();

        if (organizationId == null || organizationId.isBlank()) {
            throw new AccessDeniedException("ORG_CONTEXT_REQUIRED");
        }

        try {
            return UUID.fromString(organizationId);
        } catch (IllegalArgumentException e) {
            throw new AccessDeniedException("ORG_CONTEXT_MALFORMED", e);
        }
    }
}
