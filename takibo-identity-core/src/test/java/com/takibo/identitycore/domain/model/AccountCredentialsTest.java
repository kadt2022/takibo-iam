package com.takibo.identitycore.domain.model;

import com.takibo.identitycore.domain.vo.AccountId;
import com.takibo.identitycore.domain.vo.PasswordHash;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Règles de verrouillage du compte après échecs d'authentification. */
class AccountCredentialsTest {

    private static final AccountId ACCOUNT = AccountId.of(UUID.fromString("cccccccc-0000-0000-0000-000000000003"));
    private static final PasswordHash HASH = PasswordHash.of("$2a$10$old", "bcrypt", 1);

    private static AccountCredentials withFailures(int failures, Instant lockedUntil) {
        return AccountCredentials.builder()
                .accountId(ACCOUNT).passwordHash(HASH)
                .failedAttempts(failures).lockedUntil(lockedUntil)
                .build();
    }

    @Test
    void registerFailure_belowThreshold_countsWithoutLocking() {
        AccountCredentials after = withFailures(1, null).registerFailure(5, 900);

        assertThat(after.getFailedAttempts()).isEqualTo(2);
        assertThat(after.getLockedUntil()).isNull();
        assertThat(after.isLocked()).isFalse();
    }

    @Test
    void registerFailure_reachingThreshold_locksForTheConfiguredDuration() {
        Instant before = Instant.now();

        AccountCredentials after = withFailures(4, null).registerFailure(5, 900);

        assertThat(after.getFailedAttempts()).isEqualTo(5);
        assertThat(after.getLockedUntil()).isAfterOrEqualTo(before.plusSeconds(900));
        assertThat(after.isLocked()).isTrue();
    }

    @Test
    void registerFailure_withoutLockDuration_neverLocks() {
        AccountCredentials after = withFailures(9, null).registerFailure(5, 0);

        assertThat(after.getFailedAttempts()).isEqualTo(10);
        assertThat(after.isLocked()).isFalse();
    }

    @Test
    void registerFailure_belowThreshold_keepsAnExistingLock() {
        Instant lock = Instant.now().plusSeconds(600);

        AccountCredentials after = withFailures(0, lock).registerFailure(5, 900);

        assertThat(after.getLockedUntil()).isEqualTo(lock);
        assertThat(after.isLocked()).isTrue();
    }

    @Test
    void isLocked_expiredLock_isNoLongerLocked() {
        assertThat(withFailures(5, Instant.now().minusSeconds(1)).isLocked()).isFalse();
    }

    @Test
    void registerSuccess_clearsCounterAndLock() {
        AccountCredentials after = withFailures(5, Instant.now().plusSeconds(600)).registerSuccess();

        assertThat(after.getFailedAttempts()).isZero();
        assertThat(after.getLockedUntil()).isNull();
        assertThat(after.isLocked()).isFalse();
    }

    @Test
    void withNewPassword_replacesTheHash_andClearsTheLock() {
        PasswordHash fresh = PasswordHash.of("$2a$10$new", "bcrypt", 2);
        AccountCredentials locked = withFailures(5, Instant.now().plusSeconds(600));

        AccountCredentials after = locked.withNewPassword(fresh, true);

        assertThat(after.getPasswordHash()).isEqualTo(fresh);
        assertThat(after.isMustChangeNextLogin()).isTrue();
        assertThat(after.getFailedAttempts()).isZero();
        assertThat(after.getLockedUntil()).isNull();
        assertThat(after.getPasswordUpdatedAt()).isNotNull();
        assertThat(after.getAccountId()).isEqualTo(ACCOUNT);
        assertThat(locked.getPasswordHash()).isEqualTo(HASH);
    }
}
