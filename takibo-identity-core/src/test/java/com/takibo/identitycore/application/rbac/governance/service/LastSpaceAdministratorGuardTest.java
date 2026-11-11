package com.takibo.identitycore.application.rbac.governance.service;

import com.takibo.identitycore.domain.exception.LastAdminRemovalException;
import com.takibo.identitycore.domain.model.User;
import com.takibo.identitycore.domain.rbac.repository.SpaceAdministrationRepository;
import com.takibo.identitycore.domain.status.UserStatus;
import com.takibo.identitycore.domain.vo.AccountId;
import com.takibo.identitycore.integration.space.port.ResolvedSpaceKey;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LastSpaceAdministratorGuardTest {

    private static final UUID ORG_ID = UUID.fromString("aaaaaaaa-0000-0000-0000-000000000001");
    private static final UUID SPACE_ID = UUID.fromString("bbbbbbbb-0000-0000-0000-000000000002");
    private static final UUID TARGET = UUID.fromString("dddddddd-0000-0000-0000-000000000004");
    private static final ResolvedSpaceKey KEY = new ResolvedSpaceKey(ORG_ID, SPACE_ID, "takibo-iam", "finance");

    @Mock private SpaceAdministrationRepository administration;
    @InjectMocks private LastSpaceAdministratorGuard guard;

    @Test
    void the_last_effective_administrator_cannot_lose_their_last_path() {
        when(administration.countEffectiveAdministratorsOtherThan(ORG_ID, SPACE_ID, TARGET)).thenReturn(0L);

        assertThatThrownBy(() -> guard.assertSpaceKeepsAnAdministrator(KEY, target(UserStatus.ACTIVE), "R_SPACE_ADMIN",
                () -> false)).isInstanceOf(LastAdminRemovalException.class);
    }

    @Test
    void another_effective_administrator_allows_the_removal() {
        when(administration.countEffectiveAdministratorsOtherThan(ORG_ID, SPACE_ID, TARGET)).thenReturn(1L);

        assertThatCode(() -> guard.assertSpaceKeepsAnAdministrator(KEY, target(UserStatus.ACTIVE), "R_SPACE_ADMIN",
                () -> false)).doesNotThrowAnyException();
    }

    @Test
    void a_target_who_keeps_the_other_path_stays_an_administrator() {
        assertThatCode(() -> guard.assertSpaceKeepsAnAdministrator(KEY, target(UserStatus.ACTIVE), "G_SPACE_ADMINS",
                () -> true)).doesNotThrowAnyException();
        verify(administration, never()).countEffectiveAdministratorsOtherThan(any(), any(), any());
    }

    @Test
    void an_inactive_target_is_not_an_effective_administrator() {
        assertThatCode(() -> guard.assertSpaceKeepsAnAdministrator(KEY, target(UserStatus.DEACTIVATED), "R_SPACE_ADMIN",
                () -> false)).doesNotThrowAnyException();
        verify(administration, never()).countEffectiveAdministratorsOtherThan(any(), any(), any());
    }

    @Test
    void the_space_is_locked_before_anything_is_read() {
        AtomicBoolean readOtherPath = new AtomicBoolean();
        when(administration.countEffectiveAdministratorsOtherThan(ORG_ID, SPACE_ID, TARGET)).thenReturn(1L);

        guard.assertSpaceKeepsAnAdministrator(KEY, target(UserStatus.ACTIVE), "R_SPACE_ADMIN", () -> {
            readOtherPath.set(true);
            return false;
        });

        InOrder order = inOrder(administration);
        order.verify(administration).lockAdministrationOf(SPACE_ID);
        order.verify(administration).countEffectiveAdministratorsOtherThan(ORG_ID, SPACE_ID, TARGET);
        org.assertj.core.api.Assertions.assertThat(readOtherPath).isTrue();
    }

    private static User target(UserStatus status) {
        User user = mock(User.class);
        lenient().when(user.getStatus()).thenReturn(status);
        lenient().when(user.getAccountId()).thenReturn(AccountId.of(TARGET));
        return user;
    }
}
