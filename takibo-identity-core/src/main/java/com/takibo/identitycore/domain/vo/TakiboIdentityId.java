package com.takibo.identitycore.domain.vo;

import lombok.EqualsAndHashCode;
import lombok.Getter;

import java.util.Objects;
import java.util.UUID;

@Getter
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public final class TakiboIdentityId {

    @EqualsAndHashCode.Include
    private final UUID value;

    private TakiboIdentityId(UUID value) {
        this.value = Objects.requireNonNull(value, "TakiboIdentityId.value");
    }

    public static TakiboIdentityId of(UUID value) {
        return new TakiboIdentityId(value);
    }

    public static TakiboIdentityId newId() {
        return new TakiboIdentityId(UUID.randomUUID());
    }

    @Override
    public String toString() {
        return value.toString();
    }
}