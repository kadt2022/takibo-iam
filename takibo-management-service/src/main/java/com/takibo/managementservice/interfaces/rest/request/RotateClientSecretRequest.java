package com.takibo.managementservice.interfaces.rest.request;

import java.time.Instant;

public record RotateClientSecretRequest(
        Instant clientSecretExpiresAt
) {}
