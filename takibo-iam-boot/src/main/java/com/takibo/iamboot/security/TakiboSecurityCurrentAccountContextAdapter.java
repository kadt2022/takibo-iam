package com.takibo.iamboot.security;

import com.takibo.identitycore.integration.security.port.CurrentAccountContextCase;
import com.takibo.securitycontext.model.StandardAttributeKeys;
import com.takibo.securitycontext.spi.CurrentTakiboSecurityContextProvider;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class TakiboSecurityCurrentAccountContextAdapter implements CurrentAccountContextCase {

    private final CurrentTakiboSecurityContextProvider contextProvider;

    public TakiboSecurityCurrentAccountContextAdapter(CurrentTakiboSecurityContextProvider contextProvider) {
        this.contextProvider = contextProvider;
    }

    @Override
    public UUID requireCurrentAccountId() {
        var context = contextProvider.current();

        if (context == null) {
            throw new AccessDeniedException("ACCOUNT_CONTEXT_REQUIRED");
        }

        return context.attributes()
                .get(StandardAttributeKeys.ACCOUNT_ID, UUID.class)
                .orElseThrow(() -> new AccessDeniedException("ACCOUNT_CONTEXT_REQUIRED"));
    }
}
