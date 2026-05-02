package com.takibo.identitycore.domain.vo;

import java.util.Objects;
import java.util.UUID;

public record UserId(UUID value) {
    public UserId {
        Objects.requireNonNull(value, "User ID cannot be null");
    }

    public UUID getValue() { return value; }

    public static UserId of(UUID value) {          // ← AJOUT
        return new UserId(value);
    }

    public static UserId generate() {
        return new UserId(UUID.randomUUID());
    }

    public static UserId fromString(String id) {
        return new UserId(UUID.fromString(id));
    }
}
