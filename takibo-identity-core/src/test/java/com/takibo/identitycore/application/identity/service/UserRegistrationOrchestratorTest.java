package com.takibo.identitycore.application.identity.service;

import com.takibo.identitycore.application.identity.command.CreateUserCommand;
import com.takibo.identitycore.domain.exception.UserCreationException;
import com.takibo.identitycore.domain.model.Account;
import com.takibo.identitycore.domain.model.EmailAddress;
import com.takibo.identitycore.domain.model.SpaceContext;
import com.takibo.identitycore.domain.model.User;
import com.takibo.identitycore.domain.model.UserRegistrationResult;
import com.takibo.identitycore.domain.repository.UserRepository;
import com.takibo.identitycore.domain.service.AccountDomainService;
import com.takibo.identitycore.domain.service.UserDomainService;
import com.takibo.identitycore.domain.vo.AccountId;
import com.takibo.identitycore.domain.vo.SpaceId;
import com.takibo.identitycore.integration.space.SpaceContextVerifier;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserRegistrationOrchestratorTest {

    private static final UUID ORG_ID       = UUID.fromString("aaaaaaaa-0000-0000-0000-000000000001");
    private static final UUID SPACE_UUID   = UUID.fromString("bbbbbbbb-0000-0000-0000-000000000002");
    private static final UUID ACCOUNT_UUID = UUID.fromString("cccccccc-0000-0000-0000-000000000003");

    @Mock private SpaceContextVerifier spaceContextVerifier;
    @Mock private UserDomainService userDomainService;
    @Mock private AccountDomainService accountDomaineService;
    @Mock private BusinessRoleAssignmentService businessRoleAssignmentService;
    @Mock private UserRepository userRepository;

    @InjectMocks
    private UserRegistrationOrchestrator orchestrator;

    @Test
    void registerUser_happyPath_coordinatesAllSteps() {
        SpaceId spaceId = SpaceId.of(SPACE_UUID);
        SpaceContext spaceContext = new SpaceContext(spaceId, ORG_ID);
        AccountId accountId = AccountId.of(ACCOUNT_UUID);
        Account account = mock(Account.class);
        User user = mock(User.class);
        EmailAddress email = new EmailAddress("user@example.com");

        when(spaceContextVerifier.validateSpaceContext(SPACE_UUID)).thenReturn(spaceContext);
        when(accountDomaineService.resolveAccountForRegistration(any(), eq(ORG_ID))).thenReturn(account);
        when(account.getId()).thenReturn(accountId);
        when(account.getEmail()).thenReturn(email);
        when(userDomainService.createNativeUser(any(), eq(ORG_ID), eq(spaceId), eq(accountId))).thenReturn(user);
        when(userRepository.save(user)).thenReturn(user);

        CreateUserCommand command = CreateUserCommand.builder()
                .spaceId(SPACE_UUID)
                .email("user@example.com")
                .businessRoleCodes(List.of("MANAGER"))
                .build();

        UserRegistrationResult result = orchestrator.registerUser(command);

        assertThat(result.user()).isSameAs(user);
        assertThat(result.accountEmail()).isSameAs(email);

        verify(spaceContextVerifier).validateSpaceContext(SPACE_UUID);
        verify(accountDomaineService).resolveAccountForRegistration(command, ORG_ID);
        verify(userDomainService).createNativeUser(command, ORG_ID, spaceId, accountId);
        verify(userRepository).save(user);
        verify(businessRoleAssignmentService).assignBusinessRoles(ORG_ID, SPACE_UUID, ACCOUNT_UUID, List.of("MANAGER"));
    }

    @Test
    void registerUser_invalidSpace_throwsBeforeAnyCreation() {
        when(spaceContextVerifier.validateSpaceContext(any()))
                .thenThrow(new UserCreationException("Space not found"));

        CreateUserCommand command = CreateUserCommand.builder()
                .spaceId(SPACE_UUID)
                .email("user@example.com")
                .build();

        assertThatThrownBy(() -> orchestrator.registerUser(command))
                .isInstanceOf(UserCreationException.class)
                .hasMessage("Space not found");

        verify(accountDomaineService, never()).resolveAccountForRegistration(any(), any());
        verify(userDomainService, never()).createNativeUser(any(), any(), any(), any());
        verify(userRepository, never()).save(any());
        verify(businessRoleAssignmentService, never()).assignBusinessRoles(any(), any(), any(), any());
    }

    @Test
    void registerUser_accountResolutionFails_throwsBeforeSavingUser() {
        SpaceId spaceId = SpaceId.of(SPACE_UUID);
        SpaceContext spaceContext = new SpaceContext(spaceId, ORG_ID);

        when(spaceContextVerifier.validateSpaceContext(SPACE_UUID)).thenReturn(spaceContext);
        when(accountDomaineService.resolveAccountForRegistration(any(), any()))
                .thenThrow(new UserCreationException("Account not found"));

        CreateUserCommand command = CreateUserCommand.builder()
                .spaceId(SPACE_UUID)
                .email("user@example.com")
                .build();

        assertThatThrownBy(() -> orchestrator.registerUser(command))
                .isInstanceOf(UserCreationException.class)
                .hasMessage("Account not found");

        verify(userRepository, never()).save(any());
        verify(businessRoleAssignmentService, never()).assignBusinessRoles(any(), any(), any(), any());
    }
}
