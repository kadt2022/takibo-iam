package com.takibo.identitycore.domain.vo;

import java.util.UUID;
import java.util.Objects;


public record RoleId(UUID value) {
    public RoleId {
        Objects.requireNonNull(value, "Role ID cannot be null");
    }

    public UUID getValue() { return value; }

    public static RoleId of(UUID value) {
        return new RoleId(value);
    }

    public static RoleId generate() {
        return new RoleId(UUID.randomUUID());
    }

    public static RoleId fromString(String id) {
        return new RoleId(UUID.fromString(id));
    }
}
