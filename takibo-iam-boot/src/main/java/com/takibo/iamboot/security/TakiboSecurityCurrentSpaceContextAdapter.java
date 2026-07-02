package com.takibo.iamboot.security;

import com.takibo.identitycore.integration.security.port.CurrentSpaceContextCase;
import com.takibo.securitycontext.spi.CurrentTakiboSecurityContextProvider;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class TakiboSecurityCurrentSpaceContextAdapter implements CurrentSpaceContextCase {

    private final CurrentTakiboSecurityContextProvider contextProvider;

    public TakiboSecurityCurrentSpaceContextAdapter(CurrentTakiboSecurityContextProvider contextProvider) {
        this.contextProvider = contextProvider;
    }

    @Override
    public UUID requireCurrentSpaceId() {
        var context = contextProvider.current();

        if (context == null || context.tenant() == null) {
            throw new AccessDeniedException("SPACE_CONTEXT_REQUIRED");
        }

        String spaceId = context.tenant().spaceId();

        if (spaceId == null || spaceId.isBlank()) {
            throw new AccessDeniedException("SPACE_CONTEXT_REQUIRED");
        }

        try {
            return UUID.fromString(spaceId);
        } catch (IllegalArgumentException e) {
            throw new AccessDeniedException("SPACE_CONTEXT_MALFORMED", e);
        }
    }
}
