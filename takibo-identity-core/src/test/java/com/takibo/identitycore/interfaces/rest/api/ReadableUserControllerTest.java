package com.takibo.identitycore.interfaces.rest.api;

import com.takibo.identitycore.application.identity.command.CreateUserCommand;
import com.takibo.identitycore.application.identity.service.UserApplicationService;
import com.takibo.identitycore.domain.exception.OrganizationNotFoundException;
import com.takibo.identitycore.integration.space.port.ResolvedSpaceKey;
import com.takibo.identitycore.integration.space.port.SpaceKeyResolutionCase;
import com.takibo.identitycore.interfaces.rest.request.CreateUserRequest;
import com.takibo.identitycore.interfaces.rest.response.UserResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReadableUserControllerTest {

    private static final UUID ORG_ID   = UUID.fromString("aaaaaaaa-0000-0000-0000-000000000001");
    private static final UUID SPACE_ID = UUID.fromString("bbbbbbbb-0000-0000-0000-000000000002");
    private static final UUID USER_ID  = UUID.fromString("cccccccc-0000-0000-0000-000000000003");

    @Mock private SpaceKeyResolutionCase spaceKeyResolution;
    @Mock private UserApplicationService service;

    @InjectMocks private ReadableUserController controller;

    private CreateUserRequest request() {
        return new CreateUserRequest(
                null, "alice@takibo.io", "Str0ng!Passw0rd",
                "alice", "Alice", "Martin", null, null);
    }

    private UserResponse userResponse() {
        return new UserResponse(
                USER_ID, SPACE_ID, "alice", "alice@takibo.io",
                "Alice", "Martin", null, null,
                false, false, null, null, null, 0L);
    }

    @Test
    void resolves_then_delegates_with_resolved_space_id() {
        when(spaceKeyResolution.resolve("takibo", "finance"))
                .thenReturn(new ResolvedSpaceKey(ORG_ID, SPACE_ID, "takibo", "finance"));
        when(service.createUser(any(CreateUserCommand.class))).thenReturn(userResponse());

        ResponseEntity<UserResponse> response = controller.create("takibo", "finance", request());

        assertThat(response.getStatusCode().value()).isEqualTo(HttpStatus.CREATED.value());
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().id()).isEqualTo(USER_ID);

        // La commande déléguée porte le spaceId RÉSOLU (pas un code).
        ArgumentCaptor<CreateUserCommand> captor = ArgumentCaptor.forClass(CreateUserCommand.class);
        verify(service).createUser(captor.capture());
        assertThat(captor.getValue().spaceId()).isEqualTo(SPACE_ID);
        assertThat(captor.getValue().username()).isEqualTo("alice");
    }

    @Test
    void propagates_resolution_failure_without_calling_service() {
        when(spaceKeyResolution.resolve("ghost", "finance"))
                .thenThrow(new OrganizationNotFoundException("Organization not found: ghost"));

        assertThatThrownBy(() -> controller.create("ghost", "finance", request()))
                .isInstanceOf(OrganizationNotFoundException.class);

        verify(service, org.mockito.Mockito.never()).createUser(any());
    }
}
