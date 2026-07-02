package com.takibo.identitycore.application.identity.service;

import com.takibo.identitycore.application.identity.command.CreateUserCommand;
import com.takibo.identitycore.application.identity.mapper.UserMapper;
import com.takibo.identitycore.domain.exception.SpaceNotFoundException;
import com.takibo.identitycore.domain.model.UserRegistrationResult;
import com.takibo.identitycore.integration.security.port.CurrentOrganizationContextCase;
import com.takibo.identitycore.integration.security.port.CurrentSpaceContextCase;
import com.takibo.identitycore.integration.space.port.SpaceOwnershipGuardCase;
import com.takibo.identitycore.interfaces.rest.response.UserResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserApplicationServiceTest {

    private static final UUID ORG_A = UUID.fromString("aaaaaaaa-0000-0000-0000-000000000001");
    private static final UUID SPACE_A = UUID.fromString("cccccccc-0000-0000-0000-000000000003");
    private static final UUID SPACE_B = UUID.fromString("dddddddd-0000-0000-0000-000000000004");

    @Mock
    private UserRegistrationOrchestrator orchestrator;

    @Mock
    private UserMapper userMapper;

    @Mock
    private SpaceOwnershipGuardCase spaceOwnershipGuard;

    @Mock
    private CurrentOrganizationContextCase currentOrganizationContext;

    @Mock
    private CurrentSpaceContextCase currentSpaceContext;

    @InjectMocks
    private UserApplicationService service;

    @Test
    void createUser_sameOrg_allowed() {
        when(currentOrganizationContext.requireCurrentOrganizationId()).thenReturn(ORG_A);
        doNothing().when(spaceOwnershipGuard).assertSpaceBelongsToOrg(SPACE_A, ORG_A);
        when(currentSpaceContext.requireCurrentSpaceId()).thenReturn(SPACE_A);

        UserRegistrationResult result = mock(UserRegistrationResult.class);
        when(orchestrator.registerUser(any())).thenReturn(result);
        UserResponse expected = mock(UserResponse.class);
        when(userMapper.toUserResponse(any(), any())).thenReturn(expected);

        UserResponse response = service.createUser(command(SPACE_A));

        assertThat(response).isSameAs(expected);
        verify(spaceOwnershipGuard).assertSpaceBelongsToOrg(SPACE_A, ORG_A);
    }

    @Test
    void createUser_differentOrg_denied() {
        when(currentOrganizationContext.requireCurrentOrganizationId()).thenReturn(ORG_A);
        doThrow(new AccessDeniedException("ORG_MISMATCH"))
                .when(spaceOwnershipGuard).assertSpaceBelongsToOrg(SPACE_B, ORG_A);

        CreateUserCommand command = command(SPACE_B);

        assertThatThrownBy(() -> service.createUser(command))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessage("ORG_MISMATCH");

        verify(orchestrator, never()).registerUser(any());
    }

    @Test
    void createUser_unknownSpace_notFound() {
        when(currentOrganizationContext.requireCurrentOrganizationId()).thenReturn(ORG_A);
        doThrow(new SpaceNotFoundException(SPACE_A))
                .when(spaceOwnershipGuard).assertSpaceBelongsToOrg(SPACE_A, ORG_A);

        CreateUserCommand command = command(SPACE_A);

        assertThatThrownBy(() -> service.createUser(command))
                .isInstanceOf(SpaceNotFoundException.class);

        verify(orchestrator, never()).registerUser(any());
    }

    @Test
    void createUser_noOrgContext_denied() {
        when(currentOrganizationContext.requireCurrentOrganizationId())
                .thenThrow(new AccessDeniedException("ORG_CONTEXT_REQUIRED"));

        CreateUserCommand command = command(SPACE_A);

        assertThatThrownBy(() -> service.createUser(command))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessage("ORG_CONTEXT_REQUIRED");

        verify(spaceOwnershipGuard, never()).assertSpaceBelongsToOrg(any(), any());
        verify(orchestrator, never()).registerUser(any());
    }

    @Test
    void createUser_spaceMismatch_denied_evenInsideSameOrg() {
        // Token SPACE situé sur SPACE_A, action demandée sur SPACE_B de la MÊME org :
        // la frontière SPACE reste stricte — aucun rôle ne l'élargit.
        when(currentOrganizationContext.requireCurrentOrganizationId()).thenReturn(ORG_A);
        doNothing().when(spaceOwnershipGuard).assertSpaceBelongsToOrg(SPACE_B, ORG_A);
        when(currentSpaceContext.requireCurrentSpaceId()).thenReturn(SPACE_A);

        CreateUserCommand command = command(SPACE_B);

        assertThatThrownBy(() -> service.createUser(command))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessage("SPACE_CONTEXT_MISMATCH");

        verify(orchestrator, never()).registerUser(any());
    }

    @Test
    void createUser_noSpaceContext_denied() {
        // Token PLATFORM (aucun space_id) : fail-closed sur la route tenant.
        when(currentOrganizationContext.requireCurrentOrganizationId()).thenReturn(ORG_A);
        doNothing().when(spaceOwnershipGuard).assertSpaceBelongsToOrg(SPACE_A, ORG_A);
        when(currentSpaceContext.requireCurrentSpaceId())
                .thenThrow(new AccessDeniedException("SPACE_CONTEXT_REQUIRED"));

        CreateUserCommand command = command(SPACE_A);

        assertThatThrownBy(() -> service.createUser(command))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessage("SPACE_CONTEXT_REQUIRED");

        verify(orchestrator, never()).registerUser(any());
    }

    @Test
    void createUser_crossTenant_foreignAccount_denied() {
        when(currentOrganizationContext.requireCurrentOrganizationId()).thenReturn(ORG_A);
        doThrow(new AccessDeniedException("ORG_MISMATCH"))
                .when(spaceOwnershipGuard).assertSpaceBelongsToOrg(SPACE_B, ORG_A);

        CreateUserCommand commandWithForeignAccount = CreateUserCommand.builder()
                .spaceId(SPACE_B)
                .accountId(UUID.randomUUID())
                .email("user@orga.com")
                .build();

        assertThatThrownBy(() -> service.createUser(commandWithForeignAccount))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessage("ORG_MISMATCH");

        verify(orchestrator, never()).registerUser(any());
    }

    private CreateUserCommand command(UUID spaceId) {
        return CreateUserCommand.builder()
                .spaceId(spaceId)
                .email("user@example.com")
                .build();
    }
}
