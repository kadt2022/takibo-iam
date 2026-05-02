package com.takibo.identitycore.domain.model;

import com.takibo.identitycore.domain.vo.AccountId;
import com.takibo.identitycore.domain.vo.PasswordHash;
import com.takibo.identitycore.infrastructure.entity.AccountEntity;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;

import java.time.Clock;
import java.time.Instant;

@Getter
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@Builder(toBuilder = true)
public class AccountCredentials {

    /** Même identifiant que l'Account (relation 1–1) */
    @EqualsAndHashCode.Include
    private final AccountId accountId;

    private Account account;

    private final PasswordHash passwordHash;
    private final Instant passwordUpdatedAt;
    private final boolean mustChangeNextLogin;

    private final int failedAttempts;
    private final Instant lockedUntil;

    private final Instant createdAt;
    private final Instant updatedAt;
    @Builder.Default
    private final Long version = 0L;

    private final Clock clock;

    // --------- Règles simples
    public AccountCredentials withNewPassword(PasswordHash newHash, boolean forceChange) {
        Instant now = Instant.now();
        return this.toBuilder()
                .passwordHash(newHash)
                .passwordUpdatedAt(now)
                .mustChangeNextLogin(forceChange)
                .failedAttempts(0)
                .lockedUntil(null)
                .updatedAt(now)
                .build();
    }

    public AccountCredentials registerFailure(int maxAttempts, long lockSeconds) {
        int next = this.failedAttempts + 1;
        Instant lock = (next >= maxAttempts && lockSeconds > 0) ? Instant.now().plusSeconds(lockSeconds) : this.lockedUntil;
        return this.toBuilder()
                .failedAttempts(next)
                .lockedUntil(lock)
                .updatedAt(Instant.now())
                .build();
    }

    public boolean isLocked() {
        return lockedUntil != null && lockedUntil.isAfter(Instant.now());
    }

    public static AccountCredentials create(AccountId accountId, PasswordHash hash) {
        Instant now = Instant.now();
        return AccountCredentials.builder()
                .accountId(accountId)
                .passwordHash(hash)
                .passwordUpdatedAt(now)
                .mustChangeNextLogin(false)
                .failedAttempts(0)
                .lockedUntil(null)
                .createdAt(now)
                .updatedAt(now)
                .version(0L)
                .build();
    }

    public static AccountCredentials createNew(AccountId accountId, PasswordHash hash) {
        Instant now = Instant.now();
        return  AccountCredentials.builder()
            .accountId(accountId)
            .passwordHash(hash)
            .mustChangeNextLogin(false)
            .failedAttempts(0)
            .lockedUntil(null)
            .passwordUpdatedAt(now)
            .createdAt(now)
            .updatedAt(now)
            .version(0L)
            .build();
    }

}
