package com.takibo.identitycore.domain.vo;

import lombok.EqualsAndHashCode;
import lombok.Getter;

import java.util.Objects;

@Getter
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public final class PasswordHash {
    @EqualsAndHashCode.Include
    private final String hash;
    private final String algo;
    private final Integer version;

    private PasswordHash(String hash, String algo, Integer version) {
        this.hash = Objects.requireNonNull(hash, "hash");
        this.algo = (algo == null || algo.isBlank()) ? "bcrypt" : algo;
        this.version = version;
    }

    public static PasswordHash of(String hash, String algo, Integer version) {
        return new PasswordHash(hash, algo, version);
    }
}
