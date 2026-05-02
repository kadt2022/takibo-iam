package com.takibo.managementservice.interfaces.rest.response;

import java.time.Instant;

/**
 * oneTimePlainSecret is returned only at creation/rotation; store securely.
 * It cannot be retrieved later (only rotated).
 */
public record ClientSecretResponse(
        String clientId,
        String oneTimePlainSecret,
        Instant clientSecretExpiresAt
) {}
