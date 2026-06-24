package com.takibo.identitycore.application.identity.service;

import com.takibo.identitycore.application.identity.command.CreateUserCommand;
import com.takibo.identitycore.application.identity.command.ProvisionFounderUserCommand;
import com.takibo.identitycore.application.identity.mapper.UserMapper;
import com.takibo.identitycore.domain.model.SpaceContext;
import com.takibo.identitycore.domain.model.UserRegistrationResult;
import com.takibo.identitycore.domain.vo.SpaceId;
import com.takibo.identitycore.integration.space.SpaceContextVerifier;
import com.takibo.identitycore.interfaces.rest.response.UserResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FounderUserProvisioningServiceTest {

    private static final UUID ORG_ID     = UUID.fromString("aaaaaaaa-0000-0000-0000-000000000001");
    private static final UUID SPACE_ID   = UUID.fromString("bbbbbbbb-0000-0000-0000-000000000002");
    private static final UUID ACCOUNT_ID = UUID.fromString("cccccccc-0000-0000-0000-000000000003");

    @Mock private SpaceContextVerifier spaceContextVerifier;
    @Mock private UserRegistrationOrchestrator userRegistrationOrchestrator;
    @Mock private UserMapper userMapper;

    @InjectMocks private FounderUserProvisioningService service;

    private ProvisionFounderUserCommand command() {
        return new ProvisionFounderUserCommand(ORG_ID, SPACE_ID, ACCOUNT_ID, "founder", "Tresor", "Kadima");
    }

    @Test
    void provisions_founder_via_orchestrator_bypassing_token_org() {
        when(spaceContextVerifier.validateSpaceContext(SPACE_ID))
                .thenReturn(new SpaceContext(new SpaceId(SPACE_ID), ORG_ID));
        UserRegistrationResult result = mock(UserRegistrationResult.class);
        when(userRegistrationOrchestrator.registerUser(any(CreateUserCommand.class))).thenReturn(result);
        UserResponse expected = mock(UserResponse.class);
        when(userMapper.toUserResponse(any(), any())).thenReturn(expected);

        UserResponse actual = service.provisionFounder(command());

        assertThat(actual).isSameAs(expected);

        ArgumentCaptor<CreateUserCommand> captor = ArgumentCaptor.forClass(CreateUserCommand.class);
        verify(userRegistrationOrchestrator).registerUser(captor.capture());
        CreateUserCommand built = captor.getValue();
        assertThat(built.spaceId()).isEqualTo(SPACE_ID);
        assertThat(built.accountId()).isEqualTo(ACCOUNT_ID);
        assertThat(built.username()).isEqualTo("founder");
        assertThat(built.metadata()).containsEntry("provisioning", "organization-signup-founder");
    }

    @Test
    void rejects_when_space_does_not_belong_to_declared_org() {
        UUID otherOrg = UUID.fromString("dddddddd-0000-0000-0000-000000000009");
        when(spaceContextVerifier.validateSpaceContext(SPACE_ID))
                .thenReturn(new SpaceContext(new SpaceId(SPACE_ID), otherOrg));

        assertThatThrownBy(() -> service.provisionFounder(command()))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("SPACE_ORG_MISMATCH");

        verify(userRegistrationOrchestrator, never()).registerUser(any());
    }
}
