package com.takibo.identitycore.interfaces.rest.api;

import com.takibo.identitycore.application.rbac.governance.command.AssignUserRoleCommand;
import com.takibo.identitycore.application.rbac.governance.command.RemoveUserRoleCommand;
import com.takibo.identitycore.application.rbac.governance.port.in.UserRoleGovernanceCase;
import com.takibo.identitycore.integration.space.port.ResolvedSpaceKey;
import com.takibo.identitycore.integration.space.port.SpaceKeyResolutionCase;
import com.takibo.identitycore.interfaces.rest.request.AssignUserRoleRequest;
import com.takibo.identitycore.interfaces.rest.response.UserRoleAssignmentsResponse;
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
class ReadableUserRoleGovernanceControllerTest {

    private static final UUID ORG_ID = UUID.fromString("aaaaaaaa-0000-0000-0000-000000000001");
    private static final UUID SPACE_ID = UUID.fromString("bbbbbbbb-0000-0000-0000-000000000002");
    private static final UUID USER_ID = UUID.fromString("cccccccc-0000-0000-0000-000000000003");
    private static final ResolvedSpaceKey KEY = new ResolvedSpaceKey(ORG_ID, SPACE_ID, "takibo-iam", "finance");

    @Mock
    private SpaceKeyResolutionCase spaceKeyResolution;

    @Mock
    private UserRoleGovernanceCase userRoleGovernanceCase;

    @InjectMocks
    private ReadableUserRoleGovernanceController controller;

    @Test
    void list_resolvesReadableKeyAndDelegates() {
        UserRoleAssignmentsResponse expected = new UserRoleAssignmentsResponse(USER_ID, List.of());
        when(spaceKeyResolution.resolve("takibo-iam", "finance")).thenReturn(KEY);
        when(userRoleGovernanceCase.listDirectRoles(KEY, USER_ID)).thenReturn(expected);

        ResponseEntity<UserRoleAssignmentsResponse> response = controller.list("takibo-iam", "finance", USER_ID);

        assertThat(response.getBody()).isSameAs(expected);
        verify(userRoleGovernanceCase).listDirectRoles(KEY, USER_ID);
    }

    @Test
    void assign_buildsCommandFromRequest() {
        UserRoleAssignmentsResponse expected = new UserRoleAssignmentsResponse(USER_ID, List.of());
        when(spaceKeyResolution.resolve("takibo-iam", "finance")).thenReturn(KEY);
        AssignUserRoleCommand command = new AssignUserRoleCommand(USER_ID, "R_SPACE_ADMIN", "promotion");
        when(userRoleGovernanceCase.assignRole(KEY, command)).thenReturn(expected);

        ResponseEntity<UserRoleAssignmentsResponse> response = controller.assign(
                "takibo-iam", "finance", USER_ID, new AssignUserRoleRequest("R_SPACE_ADMIN", "promotion"));

        assertThat(response.getBody()).isSameAs(expected);
        verify(userRoleGovernanceCase).assignRole(KEY, command);
    }

    @Test
    void remove_buildsCommandFromPath() {
        UserRoleAssignmentsResponse expected = new UserRoleAssignmentsResponse(USER_ID, List.of());
        when(spaceKeyResolution.resolve("takibo-iam", "finance")).thenReturn(KEY);
        RemoveUserRoleCommand command = new RemoveUserRoleCommand(USER_ID, "R_SPACE_ADMIN", null);
        when(userRoleGovernanceCase.removeRole(KEY, command)).thenReturn(expected);

        ResponseEntity<UserRoleAssignmentsResponse> response = controller.remove(
                "takibo-iam", "finance", USER_ID, "R_SPACE_ADMIN");

        assertThat(response.getBody()).isSameAs(expected);
        verify(userRoleGovernanceCase).removeRole(KEY, command);
    }
}
