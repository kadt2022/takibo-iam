package com.takibo.identitycore.domain.rbac.model;

import java.util.UUID;

public record GroupReference(
        UUID id,
        String code
) {
}
