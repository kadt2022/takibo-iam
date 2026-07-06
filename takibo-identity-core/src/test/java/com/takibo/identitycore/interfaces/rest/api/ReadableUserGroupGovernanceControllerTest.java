package com.takibo.identitycore.interfaces.rest.api;

import com.takibo.identitycore.application.rbac.governance.command.AddUserToGroupCommand;
import com.takibo.identitycore.application.rbac.governance.command.RemoveUserFromGroupCommand;
import com.takibo.identitycore.application.rbac.governance.port.in.UserGroupGovernanceCase;
import com.takibo.identitycore.integration.space.port.ResolvedSpaceKey;
import com.takibo.identitycore.integration.space.port.SpaceKeyResolutionCase;
import com.takibo.identitycore.interfaces.rest.request.AddUserToGroupRequest;
import com.takibo.identitycore.interfaces.rest.response.UserGroupMembershipsResponse;
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
class ReadableUserGroupGovernanceControllerTest {

    private static final UUID ORG_ID = UUID.fromString("aaaaaaaa-0000-0000-0000-000000000001");
    private static final UUID SPACE_ID = UUID.fromString("bbbbbbbb-0000-0000-0000-000000000002");
    private static final UUID USER_ID = UUID.fromString("cccccccc-0000-0000-0000-000000000003");
    private static final ResolvedSpaceKey KEY = new ResolvedSpaceKey(ORG_ID, SPACE_ID, "takibo-iam", "finance");

    @Mock
    private SpaceKeyResolutionCase spaceKeyResolution;

    @Mock
    private UserGroupGovernanceCase userGroupGovernanceCase;

    @InjectMocks
    private ReadableUserGroupGovernanceController controller;

    @Test
    void list_resolvesReadableKeyAndDelegates() {
        UserGroupMembershipsResponse expected = new UserGroupMembershipsResponse(USER_ID, List.of());
        when(spaceKeyResolution.resolve("takibo-iam", "finance")).thenReturn(KEY);
        when(userGroupGovernanceCase.listDirectGroups(KEY, USER_ID)).thenReturn(expected);

        ResponseEntity<UserGroupMembershipsResponse> response = controller.list("takibo-iam", "finance", USER_ID);

        assertThat(response.getBody()).isSameAs(expected);
        verify(userGroupGovernanceCase).listDirectGroups(KEY, USER_ID);
    }

    @Test
    void add_buildsCommandFromRequest() {
        UserGroupMembershipsResponse expected = new UserGroupMembershipsResponse(USER_ID, List.of());
        when(spaceKeyResolution.resolve("takibo-iam", "finance")).thenReturn(KEY);
        AddUserToGroupCommand command = new AddUserToGroupCommand(USER_ID, "G_SPACE_ADMINS", "joins admins");
        when(userGroupGovernanceCase.addToGroup(KEY, command)).thenReturn(expected);

        ResponseEntity<UserGroupMembershipsResponse> response = controller.add(
                "takibo-iam", "finance", USER_ID, new AddUserToGroupRequest("G_SPACE_ADMINS", "joins admins"));

        assertThat(response.getBody()).isSameAs(expected);
        verify(userGroupGovernanceCase).addToGroup(KEY, command);
    }

    @Test
    void remove_buildsCommandFromPath() {
        UserGroupMembershipsResponse expected = new UserGroupMembershipsResponse(USER_ID, List.of());
        when(spaceKeyResolution.resolve("takibo-iam", "finance")).thenReturn(KEY);
        RemoveUserFromGroupCommand command = new RemoveUserFromGroupCommand(USER_ID, "G_SPACE_ADMINS", null);
        when(userGroupGovernanceCase.removeFromGroup(KEY, command)).thenReturn(expected);

        ResponseEntity<UserGroupMembershipsResponse> response = controller.remove(
                "takibo-iam", "finance", USER_ID, "G_SPACE_ADMINS");

        assertThat(response.getBody()).isSameAs(expected);
        verify(userGroupGovernanceCase).removeFromGroup(KEY, command);
    }
}
