package com.takibo.securitycontext.model;

import java.time.Instant;

import static com.takibo.securitycontext.validation.TakiboAsserts.*;

public record TemporalContext(
        Instant issuedAt,
        String correlationId,
        String issuer,
        int schemaVersion
) {
    public TemporalContext {
        notNull(issuedAt, "issuedAt is required");
        min(schemaVersion, 1, "schemaVersion");
        maxLength(correlationId, 128, "correlationId too long");
        maxLength(issuer, 256, "issuer too long");
    }
}
