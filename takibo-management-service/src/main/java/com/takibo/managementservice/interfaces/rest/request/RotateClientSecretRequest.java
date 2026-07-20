package com.takibo.managementservice.interfaces.rest.request;

import jakarta.validation.constraints.Future;

import java.time.Instant;

public record RotateClientSecretRequest(
        @Future Instant clientSecretExpiresAt
) {}
