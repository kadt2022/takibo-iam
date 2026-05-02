package com.takibo.identitycore.domain.vo;

import java.util.Objects;
import java.util.UUID;

public final class SpaceId {
    private final UUID value;

    public SpaceId(UUID value) {
        this.value = Objects.requireNonNull(value, "SpaceId value");
    }

    public static SpaceId of(UUID value) {
        return new SpaceId(value);
    }

    public UUID value() {
        return value;
    }

    public UUID getValue() { return value; }


    @Override public String toString() { return value.toString(); }

    @Override public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof SpaceId other)) return false;
        return value.equals(other.value);
    }

    @Override public int hashCode() { return value.hashCode(); }
}