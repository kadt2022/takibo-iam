package com.takibo.identitycore.integration.security.port;

import java.util.UUID;

public interface CurrentOrganizationContextCase {

    /**
     * @throws org.springframework.security.access.AccessDeniedException if no organization context is available
     */
    UUID requireCurrentOrganizationId();
}
