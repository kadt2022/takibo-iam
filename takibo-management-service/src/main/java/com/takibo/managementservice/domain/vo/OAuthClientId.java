package com.takibo.managementservice.domain.vo;

import lombok.EqualsAndHashCode;
import lombok.Getter;

import java.util.Objects;
import java.util.UUID;

@Getter
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public final class OAuthClientId {
    @EqualsAndHashCode.Include
    private final UUID value;

    public OAuthClientId(UUID value) { this.value = Objects.requireNonNull(value, "OAuthClientId.value"); }
    public static OAuthClientId of(UUID value) { return new OAuthClientId(value); }
    public static OAuthClientId newId() { return new OAuthClientId(UUID.randomUUID()); }
    @Override public String toString(){ return value.toString(); }
}
