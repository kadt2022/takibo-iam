package com.takibo.identitycore.domain.vo;

import lombok.EqualsAndHashCode;
import lombok.Getter;

import java.util.Objects;
import java.util.UUID;

@Getter
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public final class AccountIdentityId {
    @EqualsAndHashCode.Include
    private final UUID value;

    private AccountIdentityId(UUID value) {
        this.value = Objects.requireNonNull(value, "AccountIdentityId.value");
    }

    public static AccountIdentityId of(UUID value) { return new AccountIdentityId(value); }
    public static AccountIdentityId newId() { return new AccountIdentityId(UUID.randomUUID()); }

    @Override public String toString() { return value.toString(); }
}
