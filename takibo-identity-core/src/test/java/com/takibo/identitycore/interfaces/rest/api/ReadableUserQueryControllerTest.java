package com.takibo.identitycore.interfaces.rest.api;

import com.takibo.identitycore.application.identity.port.UserQueryCase;
import com.takibo.identitycore.domain.status.UserStatus;
import com.takibo.identitycore.domain.type.UserType;
import com.takibo.identitycore.integration.space.port.ResolvedSpaceKey;
import com.takibo.identitycore.integration.space.port.SpaceKeyResolutionCase;
import com.takibo.identitycore.interfaces.rest.response.UserPageResponse;
import com.takibo.identitycore.interfaces.rest.response.UserResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReadableUserQueryControllerTest {

    private static final UUID ORG_ID = UUID.fromString("aaaaaaaa-0000-0000-0000-000000000001");
    private static final UUID SPACE_ID = UUID.fromString("bbbbbbbb-0000-0000-0000-000000000002");
    private static final UUID USER_ID = UUID.fromString("cccccccc-0000-0000-0000-000000000003");
    private static final ResolvedSpaceKey KEY = new ResolvedSpaceKey(ORG_ID, SPACE_ID, "takibo-iam", "finance");

    @Mock
    private SpaceKeyResolutionCase spaceKeyResolution;

    @Mock
    private UserQueryCase userQueryCase;

    @InjectMocks
    private ReadableUserQueryController controller;

    @Test
    void list_resolvesReadableKeyAndDelegatesFilters() {
        UserPageResponse expected = new UserPageResponse(List.of(), 1, 10, 0, 0);
        when(spaceKeyResolution.resolve("takibo-iam", "finance")).thenReturn(KEY);
        when(userQueryCase.listUsers(KEY, UserStatus.ACTIVE, UserType.NATIVE, "ali", 1, 10)).thenReturn(expected);

        ResponseEntity<UserPageResponse> response = controller.list(
                "takibo-iam",
                "finance",
                1,
                10,
                UserStatus.ACTIVE,
                UserType.NATIVE,
                "ali"
        );

        assertThat(response.getBody()).isSameAs(expected);
        verify(userQueryCase).listUsers(KEY, UserStatus.ACTIVE, UserType.NATIVE, "ali", 1, 10);
    }

    @Test
    void get_resolvesReadableKeyAndDelegatesUserId() {
        UserResponse expected = new UserResponse(USER_ID, SPACE_ID, "alice", "alice@takibo.io", "Alice", "Martin",
                UserStatus.ACTIVE, UserType.NATIVE, false, false, null, null, null, 0L);
        when(spaceKeyResolution.resolve("takibo-iam", "finance")).thenReturn(KEY);
        when(userQueryCase.getUser(KEY, USER_ID)).thenReturn(expected);

        ResponseEntity<UserResponse> response = controller.get("takibo-iam", "finance", USER_ID);

        assertThat(response.getBody()).isSameAs(expected);
        verify(userQueryCase).getUser(KEY, USER_ID);
    }
}
