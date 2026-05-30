package com.takibo.identitycore.domain.model;

import com.takibo.identitycore.domain.status.UserStatus;
import com.takibo.identitycore.domain.vo.AccountId;
import com.takibo.identitycore.domain.vo.SpaceId;
import com.takibo.identitycore.domain.vo.UserId;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

class UserTest {

    private static final UUID ORG_ID   = UUID.fromString("aaaaaaaa-0000-0000-0000-000000000001");
    private static final SpaceId SPACE = SpaceId.of(UUID.fromString("bbbbbbbb-0000-0000-0000-000000000002"));
    private static final AccountId ACCOUNT = AccountId.of(UUID.fromString("cccccccc-0000-0000-0000-000000000003"));
    private static final UserId USER_ID = UserId.generate();
    private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");

    @Test
    void createNative_setsTypeAndFields() {
        User user = User.createNative(USER_ID, ORG_ID, SPACE, ACCOUNT,
                "jdoe", "John", "Doe",
                UserStatus.ACTIVE, false, false, null, NOW, NOW, Map.of());

        assertThat(user.getType()).isEqualTo(UserType.NATIVE);
        assertThat(user.getStatus()).isEqualTo(UserStatus.ACTIVE);
        assertThat(user.getId()).isEqualTo(USER_ID);
        assertThat(user.getOrgId()).isEqualTo(ORG_ID);
        assertThat(user.getSpaceId()).isEqualTo(SPACE);
        assertThat(user.getAccountId()).isEqualTo(ACCOUNT);
        assertThat(user.getUsername()).isEqualTo("jdoe");
        assertThat(user.getFirstName()).isEqualTo("John");
        assertThat(user.getLastName()).isEqualTo("Doe");
    }

    @Test
    void createNative_nullStatus_defaultsToActive() {
        User user = User.createNative(USER_ID, ORG_ID, SPACE, ACCOUNT,
                "jdoe", null, null, null, false, false, null, NOW, NOW, null);

        assertThat(user.getStatus()).isEqualTo(UserStatus.ACTIVE);
    }

    @Test
    void createNative_nullId_throwsNPE() {
        assertThatNullPointerException()
                .isThrownBy(() -> User.createNative(null, ORG_ID, SPACE, ACCOUNT,
                        "jdoe", null, null, null, false, false, null, NOW, NOW, null))
                .withMessage("id");
    }

    @Test
    void createNative_nullUsername_throwsNPE() {
        assertThatNullPointerException()
                .isThrownBy(() -> User.createNative(USER_ID, ORG_ID, SPACE, ACCOUNT,
                        null, null, null, null, false, false, null, NOW, NOW, null))
                .withMessage("username");
    }

    @Test
    void createNative_nullOrgId_throwsNPE() {
        assertThatNullPointerException()
                .isThrownBy(() -> User.createNative(USER_ID, null, SPACE, ACCOUNT,
                        "jdoe", null, null, null, false, false, null, NOW, NOW, null))
                .withMessage("orgId");
    }

    @Test
    void createFederated_setsCorrectTypeAndDefaults() {
        User user = User.createFederated(ORG_ID, SPACE, ACCOUNT,
                "fed_user", "Alice", "Smith", Map.of());

        assertThat(user.getType()).isEqualTo(UserType.FEDERATED);
        assertThat(user.getStatus()).isEqualTo(UserStatus.ACTIVE);
        assertThat(user.isMfaEnabled()).isFalse();
        assertThat(user.isPasswordExpired()).isFalse();
        assertThat(user.getId()).isNotNull();
        assertThat(user.getCreatedAt()).isNotNull();
        assertThat(user.getUpdatedAt()).isNotNull();
    }

    @Test
    void createService_setsMachineAccountTypeAndNullNames() {
        User user = User.createService(ORG_ID, SPACE, ACCOUNT, "svc_account", Map.of());

        assertThat(user.getType()).isEqualTo(UserType.MACHINE_ACCOUNT);
        assertThat(user.getFirstName()).isNull();
        assertThat(user.getLastName()).isNull();
        assertThat(user.getStatus()).isEqualTo(UserStatus.ACTIVE);
        assertThat(user.getUsername()).isEqualTo("svc_account");
    }

    @Test
    void createGuest_setsGuestTypeAndNames() {
        User user = User.createGuest(ORG_ID, SPACE, ACCOUNT,
                "guest_user", "Guest", "Person", Map.of());

        assertThat(user.getType()).isEqualTo(UserType.GUEST);
        assertThat(user.getStatus()).isEqualTo(UserStatus.ACTIVE);
        assertThat(user.getUsername()).isEqualTo("guest_user");
        assertThat(user.getFirstName()).isEqualTo("Guest");
        assertThat(user.getLastName()).isEqualTo("Person");
    }
}
