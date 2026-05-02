package com.takibo.identitycore.domain.vo;

import lombok.EqualsAndHashCode;
import lombok.Getter;

import java.util.Objects;
import java.util.UUID;



@Getter
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class OrganizationId {

    @EqualsAndHashCode.Include
    private final UUID value;

    public OrganizationId(UUID value) {
        this.value = Objects.requireNonNull(value, "AccountId.value");
    }

    public static OrganizationId of(UUID value) { return new OrganizationId(value); }

    @Override public String toString() { return value.toString(); }
}
