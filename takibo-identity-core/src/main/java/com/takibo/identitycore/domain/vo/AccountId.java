package com.takibo.identitycore.domain.vo;

import lombok.EqualsAndHashCode;
import lombok.Getter;

import java.util.Objects;
import java.util.UUID;

@Getter
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public final class AccountId {
    @EqualsAndHashCode.Include
    private final UUID value;

    public UUID getValue() { return value; }


    public AccountId(UUID value) {
        this.value = Objects.requireNonNull(value, "AccountId.value");
    }

    public static AccountId of(UUID value) { return new AccountId(value); }
    public static AccountId newId() { return new AccountId(UUID.randomUUID()); }

    @Override public String toString() { return value.toString(); }
}
