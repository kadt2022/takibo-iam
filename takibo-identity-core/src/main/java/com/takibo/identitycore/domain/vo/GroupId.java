package com.takibo.identitycore.domain.vo;

import java.util.Objects;
import java.util.UUID;

public record GroupId(UUID value) {
    public GroupId {
        Objects.requireNonNull(value, "Group ID cannot be null");
    }

    public UUID getValue() { return value; }

    public static GroupId of(UUID value) {          // ← AJOUT
        return new GroupId(value);
    }

    public static GroupId generate() {
        return new GroupId(UUID.randomUUID());
    }

    public static GroupId fromString(String id) {
        return new GroupId(UUID.fromString(id));
    }
}

