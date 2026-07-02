package com.takibo.identitycore.interfaces.rest.api;

import com.takibo.identitycore.application.identity.command.ChangeUserStatusCommand;
import com.takibo.identitycore.application.identity.command.UpdateUserProfileCommand;
import com.takibo.identitycore.application.identity.port.UserLifecycleCase;
import com.takibo.identitycore.domain.status.UserStatus;
import com.takibo.identitycore.integration.space.port.ResolvedSpaceKey;
import com.takibo.identitycore.integration.space.port.SpaceKeyResolutionCase;
import com.takibo.identitycore.interfaces.rest.request.UpdateUserProfileRequest;
import com.takibo.identitycore.interfaces.rest.request.UserStatusChangeRequest;
import com.takibo.identitycore.interfaces.rest.response.UserResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReadableUserLifecycleControllerTest {

    private static final UUID ORG_ID = UUID.fromString("aaaaaaaa-0000-0000-0000-000000000001");
    private static final UUID SPACE_ID = UUID.fromString("bbbbbbbb-0000-0000-0000-000000000002");
    private static final UUID USER_ID = UUID.fromString("cccccccc-0000-0000-0000-000000000003");
    private static final ResolvedSpaceKey KEY = new ResolvedSpaceKey(ORG_ID, SPACE_ID, "takibo-iam", "finance");

    @Mock
    private SpaceKeyResolutionCase spaceKeyResolution;

    @Mock
    private UserLifecycleCase userLifecycleCase;

    @InjectMocks
    private ReadableUserLifecycleController controller;

    @Test
    void updateProfile_resolvesSpaceAndDelegatesCommand() {
        UserResponse expected = response(UserStatus.ACTIVE);
        when(spaceKeyResolution.resolve("takibo-iam", "finance")).thenReturn(KEY);
        when(userLifecycleCase.updateProfile(any(), any())).thenReturn(expected);

        ResponseEntity<UserResponse> response = controller.updateProfile(
                "takibo-iam",
                "finance",
                USER_ID,
                new UpdateUserProfileRequest("alice", "Alice", "Martin", Map.of("team", "ops"))
        );

        assertThat(response.getBody()).isSameAs(expected);
        ArgumentCaptor<UpdateUserProfileCommand> captor = ArgumentCaptor.forClass(UpdateUserProfileCommand.class);
        verify(userLifecycleCase).updateProfile(org.mockito.Mockito.eq(KEY), captor.capture());
        assertThat(captor.getValue().userId()).isEqualTo(USER_ID);
        assertThat(captor.getValue().username()).isEqualTo("alice");
        assertThat(captor.getValue().firstName()).isEqualTo("Alice");
        assertThat(captor.getValue().lastName()).isEqualTo("Martin");
        assertThat(captor.getValue().metadata()).containsEntry("team", "ops");
    }

    @Test
    void suspend_delegatesSuspendedStatusWithReason() {
        assertStatusChange(UserStatus.SUSPENDED, "risk",
                () -> controller.suspend("takibo-iam", "finance", USER_ID, new UserStatusChangeRequest("risk")));
    }

    @Test
    void activate_delegatesActiveStatusWithNullReasonWhenRequestMissing() {
        assertStatusChange(UserStatus.ACTIVE, null,
                () -> controller.activate("takibo-iam", "finance", USER_ID, null));
    }

    @Test
    void lock_delegatesLockedStatus() {
        assertStatusChange(UserStatus.LOCKED, "too many attempts",
                () -> controller.lock("takibo-iam", "finance", USER_ID, new UserStatusChangeRequest("too many attempts")));
    }

    @Test
    void deactivate_delegatesDeactivatedStatus() {
        assertStatusChange(UserStatus.DEACTIVATED, "offboarding",
                () -> controller.deactivate("takibo-iam", "finance", USER_ID, new UserStatusChangeRequest("offboarding")));
    }

    private void assertStatusChange(UserStatus targetStatus, String reason, StatusCall call) {
        UserResponse expected = response(targetStatus);
        when(spaceKeyResolution.resolve("takibo-iam", "finance")).thenReturn(KEY);
        when(userLifecycleCase.changeStatus(any(), any())).thenReturn(expected);

        ResponseEntity<UserResponse> response = call.invoke();

        assertThat(response.getBody()).isNotNull();
        ArgumentCaptor<ChangeUserStatusCommand> captor = ArgumentCaptor.forClass(ChangeUserStatusCommand.class);
        verify(userLifecycleCase).changeStatus(org.mockito.Mockito.eq(KEY), captor.capture());
        assertThat(captor.getValue().userId()).isEqualTo(USER_ID);
        assertThat(captor.getValue().targetStatus()).isEqualTo(targetStatus);
        assertThat(captor.getValue().reason()).isEqualTo(reason);
    }

    private UserResponse response(UserStatus status) {
        return new UserResponse(
                USER_ID,
                SPACE_ID,
                "alice",
                "alice@takibo.io",
                "Alice",
                "Martin",
                status,
                null,
                false,
                false,
                null,
                null,
                null,
                0L
        );
    }

    private interface StatusCall {
        ResponseEntity<UserResponse> invoke();
    }
}
