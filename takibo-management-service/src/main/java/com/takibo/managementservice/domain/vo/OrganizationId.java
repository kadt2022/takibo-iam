package com.takibo.managementservice.domain.vo;

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
    public static OrganizationId newId() { return new OrganizationId(UUID.randomUUID()); }

    @Override public String toString() { return value.toString(); }
}
