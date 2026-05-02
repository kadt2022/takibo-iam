package com.takibo.identitycore.domain.model;

import java.util.UUID;

public record Identity(
        IdentityType type,
        UUID id
) {}