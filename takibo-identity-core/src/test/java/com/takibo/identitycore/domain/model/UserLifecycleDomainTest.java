package com.takibo.identitycore.domain.model;

import com.takibo.identitycore.domain.exception.InvalidStatusTransitionException;
import com.takibo.identitycore.domain.status.UserStatus;
import com.takibo.identitycore.domain.vo.AccountId;
import com.takibo.identitycore.domain.vo.SpaceId;
import com.takibo.identitycore.domain.vo.UserId;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UserLifecycleDomainTest {

    private User user(UserStatus status) {
        return User.builder()
                .id(UserId.generate())
                .orgId(UUID.randomUUID())
                .spaceId(SpaceId.of(UUID.randomUUID()))
                .accountId(AccountId.newId())
                .username("jdoe")
                .firstName("John")
                .lastName("Doe")
                .status(status)
                .type(UserType.NATIVE)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .metadata(Map.of())
                .build();
    }

    @Test
    void changeStatus_allowedTransition_returnsUpdatedCopy() {
        User active = user(UserStatus.ACTIVE);

        User suspended = active.changeStatus(UserStatus.SUSPENDED);

        assertThat(suspended.getStatus()).isEqualTo(UserStatus.SUSPENDED);
        assertThat(active.getStatus()).isEqualTo(UserStatus.ACTIVE);
        assertThat(suspended.getId()).isEqualTo(active.getId());
    }

    @Test
    void changeStatus_coversWholeLifecycle() {
        assertThat(user(UserStatus.ACTIVE).changeStatus(UserStatus.LOCKED).getStatus())
                .isEqualTo(UserStatus.LOCKED);
        assertThat(user(UserStatus.SUSPENDED).changeStatus(UserStatus.ACTIVE).getStatus())
                .isEqualTo(UserStatus.ACTIVE);
        assertThat(user(UserStatus.LOCKED).changeStatus(UserStatus.ACTIVE).getStatus())
                .isEqualTo(UserStatus.ACTIVE);
        assertThat(user(UserStatus.PENDING_ACTIVATION).changeStatus(UserStatus.ACTIVE).getStatus())
                .isEqualTo(UserStatus.ACTIVE);
        assertThat(user(UserStatus.ACTIVE).changeStatus(UserStatus.DEACTIVATED).getStatus())
                .isEqualTo(UserStatus.DEACTIVATED);
    }

    @Test
    void changeStatus_deactivatedIsTerminal() {
        User deactivated = user(UserStatus.DEACTIVATED);

        assertThatThrownBy(() -> deactivated.changeStatus(UserStatus.ACTIVE))
                .isInstanceOf(InvalidStatusTransitionException.class)
                .hasMessageContaining("DEACTIVATED")
                .hasMessageContaining("ACTIVE");
    }

    @Test
    void changeStatus_invalidTransition_rejected() {
        User suspended = user(UserStatus.SUSPENDED);

        assertThatThrownBy(() -> suspended.changeStatus(UserStatus.LOCKED))
                .isInstanceOf(InvalidStatusTransitionException.class);
    }

    @Test
    void updateProfile_changesLocalFace_keepsIdentityAnchors() {
        User original = user(UserStatus.ACTIVE);

        User updated = original.updateProfile("john.doe", "Johnny", "DOE",
                Map.of("department", "finance"));

        assertThat(updated.getUsername()).isEqualTo("john.doe");
        assertThat(updated.getFirstName()).isEqualTo("Johnny");
        assertThat(updated.getLastName()).isEqualTo("DOE");
        assertThat(updated.getMetadata()).containsEntry("department", "finance");

        assertThat(updated.getId()).isEqualTo(original.getId());
        assertThat(updated.getOrgId()).isEqualTo(original.getOrgId());
        assertThat(updated.getSpaceId()).isEqualTo(original.getSpaceId());
        assertThat(updated.getAccountId()).isEqualTo(original.getAccountId());
        assertThat(updated.getStatus()).isEqualTo(original.getStatus());
    }
}
