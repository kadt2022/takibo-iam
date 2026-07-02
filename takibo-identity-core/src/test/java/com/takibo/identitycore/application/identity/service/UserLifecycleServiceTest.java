package com.takibo.identitycore.application.identity.service;

import com.takibo.identitycore.application.identity.command.ChangeUserStatusCommand;
import com.takibo.identitycore.application.identity.command.UpdateUserProfileCommand;
import com.takibo.identitycore.application.identity.mapper.UserMapper;
import com.takibo.identitycore.domain.exception.InvalidStatusTransitionException;
import com.takibo.identitycore.domain.exception.UserAlreadyExistsException;
import com.takibo.identitycore.domain.exception.UserNotFoundException;
import com.takibo.identitycore.domain.model.Account;
import com.takibo.identitycore.domain.model.EmailAddress;
import com.takibo.identitycore.domain.model.User;
import com.takibo.identitycore.domain.repository.AccountRepository;
import com.takibo.identitycore.domain.repository.UserRepository;
import com.takibo.identitycore.domain.model.UserType;
import com.takibo.identitycore.domain.status.UserStatus;
import com.takibo.identitycore.domain.vo.AccountId;
import com.takibo.identitycore.domain.vo.SpaceId;
import com.takibo.identitycore.domain.vo.UserId;
import com.takibo.identitycore.integration.security.SpaceBoundaryGuard;
import com.takibo.identitycore.integration.space.SpaceContextVerifier;
import com.takibo.identitycore.integration.space.port.ResolvedSpaceKey;
import com.takibo.identitycore.interfaces.rest.response.UserResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserLifecycleServiceTest {

    private static final UUID ORG_ID = UUID.fromString("aaaaaaaa-0000-0000-0000-000000000001");
    private static final UUID SPACE_ID = UUID.fromString("bbbbbbbb-0000-0000-0000-000000000002");
    private static final UUID OTHER_SPACE_ID = UUID.fromString("eeeeeeee-0000-0000-0000-000000000005");
    private static final UUID USER_ID = UUID.fromString("dddddddd-0000-0000-0000-000000000004");
    private static final ResolvedSpaceKey KEY =
            new ResolvedSpaceKey(ORG_ID, SPACE_ID, "takibo-iam", "finance");

    @Mock private SpaceContextVerifier spaceContextVerifier;
    @Mock private SpaceBoundaryGuard spaceBoundaryGuard;
    @Mock private UserRepository userRepository;
    @Mock private AccountRepository accountRepository;
    @Mock private UserMapper userMapper;

    @InjectMocks
    private UserLifecycleService service;

    private final AccountId accountId = AccountId.newId();

    @BeforeEach
    void setUp() {
        lenient().when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        Account account = mock(Account.class);
        lenient().when(account.getEmail()).thenReturn(new EmailAddress("john@example.com"));
        lenient().when(accountRepository.findById(accountId)).thenReturn(Optional.of(account));
        lenient().when(userMapper.toUserResponse(any(User.class), any(EmailAddress.class)))
                .thenReturn(mock(UserResponse.class));
    }

    private User user(UserStatus status, UUID spaceId) {
        return User.builder()
                .id(new UserId(USER_ID))
                .orgId(ORG_ID)
                .spaceId(SpaceId.of(spaceId))
                .accountId(accountId)
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

    private void givenUserInSpace(UserStatus status) {
        when(userRepository.findById(new UserId(USER_ID)))
                .thenReturn(Optional.of(user(status, SPACE_ID)));
    }

    private User savedUser() {
        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        return captor.getValue();
    }

    // ===== Profil =====

    @Test
    void updateProfile_valid_updatesLocalFace() {
        givenUserInSpace(UserStatus.ACTIVE);
        when(userRepository.existsByUsernameIgnoreCaseAndIdNot(any(), eq("john.doe"), eq(USER_ID)))
                .thenReturn(false);

        service.updateProfile(KEY, new UpdateUserProfileCommand(
                USER_ID, "john.doe", "Johnny", null, Map.of("department", "finance")));

        User saved = savedUser();
        assertThat(saved.getUsername()).isEqualTo("john.doe");
        assertThat(saved.getFirstName()).isEqualTo("Johnny");
        assertThat(saved.getLastName()).isEqualTo("Doe");
        assertThat(saved.getMetadata()).containsEntry("department", "finance");
        assertThat(saved.getAccountId()).isEqualTo(accountId);
        assertThat(saved.getSpaceId().value()).isEqualTo(SPACE_ID);
    }

    @Test
    void updateProfile_nullFields_keepExistingValues() {
        givenUserInSpace(UserStatus.ACTIVE);

        service.updateProfile(KEY, new UpdateUserProfileCommand(USER_ID, null, null, null, null));

        User saved = savedUser();
        assertThat(saved.getUsername()).isEqualTo("jdoe");
        assertThat(saved.getFirstName()).isEqualTo("John");
        assertThat(saved.getLastName()).isEqualTo("Doe");
    }

    @Test
    void updateProfile_duplicateUsername_conflict() {
        givenUserInSpace(UserStatus.ACTIVE);
        when(userRepository.existsByUsernameIgnoreCaseAndIdNot(any(), eq("taken"), eq(USER_ID)))
                .thenReturn(true);

        UpdateUserProfileCommand command =
                new UpdateUserProfileCommand(USER_ID, "taken", null, null, null);

        assertThatThrownBy(() -> service.updateProfile(KEY, command))
                .isInstanceOf(UserAlreadyExistsException.class);

        verify(userRepository, never()).save(any());
    }

    // ===== Statut =====

    @Test
    void suspendActiveUser_becomesSuspended() {
        givenUserInSpace(UserStatus.ACTIVE);

        service.changeStatus(KEY, new ChangeUserStatusCommand(USER_ID, UserStatus.SUSPENDED, "Policy violation"));

        assertThat(savedUser().getStatus()).isEqualTo(UserStatus.SUSPENDED);
    }

    @Test
    void lockActiveUser_becomesLocked() {
        givenUserInSpace(UserStatus.ACTIVE);

        service.changeStatus(KEY, new ChangeUserStatusCommand(USER_ID, UserStatus.LOCKED, null));

        assertThat(savedUser().getStatus()).isEqualTo(UserStatus.LOCKED);
    }

    @Test
    void activateSuspendedUser_becomesActive() {
        givenUserInSpace(UserStatus.SUSPENDED);

        service.changeStatus(KEY, new ChangeUserStatusCommand(USER_ID, UserStatus.ACTIVE, null));

        assertThat(savedUser().getStatus()).isEqualTo(UserStatus.ACTIVE);
    }

    @Test
    void deactivateActiveUser_becomesDeactivated() {
        givenUserInSpace(UserStatus.ACTIVE);

        service.changeStatus(KEY, new ChangeUserStatusCommand(USER_ID, UserStatus.DEACTIVATED, "User left organization"));

        assertThat(savedUser().getStatus()).isEqualTo(UserStatus.DEACTIVATED);
    }

    @Test
    void invalidTransition_conflict_nothingSaved() {
        givenUserInSpace(UserStatus.DEACTIVATED);

        ChangeUserStatusCommand command =
                new ChangeUserStatusCommand(USER_ID, UserStatus.ACTIVE, null);

        assertThatThrownBy(() -> service.changeStatus(KEY, command))
                .isInstanceOf(InvalidStatusTransitionException.class);

        verify(userRepository, never()).save(any());
    }

    // ===== Frontière =====

    @Test
    void userFromAnotherSpace_isNotFound_antiEnumeration() {
        when(userRepository.findById(new UserId(USER_ID)))
                .thenReturn(Optional.of(user(UserStatus.ACTIVE, OTHER_SPACE_ID)));

        ChangeUserStatusCommand command =
                new ChangeUserStatusCommand(USER_ID, UserStatus.SUSPENDED, null);

        assertThatThrownBy(() -> service.changeStatus(KEY, command))
                .isInstanceOf(UserNotFoundException.class);

        verify(userRepository, never()).save(any());
    }

    @Test
    void missingUser_isNotFound() {
        when(userRepository.findById(new UserId(USER_ID))).thenReturn(Optional.empty());

        UpdateUserProfileCommand command =
                new UpdateUserProfileCommand(USER_ID, "x", null, null, null);

        assertThatThrownBy(() -> service.updateProfile(KEY, command))
                .isInstanceOf(UserNotFoundException.class);
    }

    @Test
    void boundaryGuard_runsBeforeUserLookup() {
        doThrow(new org.springframework.security.access.AccessDeniedException("SPACE_CONTEXT_MISMATCH"))
                .when(spaceBoundaryGuard).assertTokenMatches(KEY);

        ChangeUserStatusCommand command =
                new ChangeUserStatusCommand(USER_ID, UserStatus.SUSPENDED, null);

        assertThatThrownBy(() -> service.changeStatus(KEY, command))
                .isInstanceOf(org.springframework.security.access.AccessDeniedException.class);

        verifyNoInteractions(userRepository);
    }
}
