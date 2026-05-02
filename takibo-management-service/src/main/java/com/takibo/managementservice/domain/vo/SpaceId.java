package com.takibo.managementservice.domain.vo;

import java.util.Objects;
import java.util.UUID;

public final class SpaceId {
  private final UUID value;

  private SpaceId(UUID value) {
    this.value = Objects.requireNonNull(value, "SpaceId value");
  }

  public static SpaceId newId() {
    return new SpaceId(UUID.randomUUID());
  }

  public static SpaceId of(UUID value) {
    return new SpaceId(value);
  }

  public UUID value() {
    return value;
  }

  @Override public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    SpaceId other = (SpaceId) o;
    return value.equals(other.value);
  }

  @Override public int hashCode() {
    return value.hashCode();
  }

  @Override public String toString() {
    return value.toString();
  }
}
