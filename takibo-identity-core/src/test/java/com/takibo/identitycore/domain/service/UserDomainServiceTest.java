package com.takibo.identitycore.domain.service;

import com.takibo.identitycore.application.identity.command.CreateUserCommand;
import com.takibo.identitycore.domain.exception.UserAlreadyExistsException;
import com.takibo.identitycore.domain.model.User;
import com.takibo.identitycore.domain.model.UserType;
import com.takibo.identitycore.domain.repository.UserRepository;
import com.takibo.identitycore.domain.status.UserStatus;
import com.takibo.identitycore.domain.vo.AccountId;
import com.takibo.identitycore.domain.vo.SpaceId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserDomainServiceTest {

    private static final UUID ORG_UUID = UUID.fromString("aaaaaaaa-0000-0000-0000-000000000001");
    private static final SpaceId SPACE = SpaceId.of(UUID.fromString("bbbbbbbb-0000-0000-0000-000000000002"));
    private static final AccountId ACCOUNT = AccountId.of(UUID.fromString("cccccccc-0000-0000-0000-000000000003"));
    private static final Instant NOW = Instant.parse("2026-04-01T08:00:00Z");

    @Mock
    private UserRepository userRepository;

    private UserDomainService service;

    @BeforeEach
    void setUp() {
        service = new UserDomainService(userRepository, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private static CreateUserCommand command() {
        return new CreateUserCommand(SPACE.value(), ACCOUNT.getValue(), "jane@example.com", "secret",
                "jane", "Jane", "Doe", List.of(), List.of(), Map.of("dept", "audit"));
    }

    @Test
    void createNativeUser_buildsAnActiveNativeUserAnchoredToOrgSpaceAndAccount() {
        when(userRepository.findByUsername(SPACE, "jane")).thenReturn(Optional.empty());
        when(userRepository.existsBySpaceAndAccount(SPACE, ACCOUNT)).thenReturn(false);

        User user = service.createNativeUser(command(), ORG_UUID, SPACE, ACCOUNT);

        assertThat(user.getId()).isNotNull();
        assertThat(user.getOrgId()).isEqualTo(ORG_UUID);
        assertThat(user.getSpaceId()).isEqualTo(SPACE);
        assertThat(user.getAccountId()).isEqualTo(ACCOUNT);
        assertThat(user.getUsername()).isEqualTo("jane");
        assertThat(user.getFirstName()).isEqualTo("Jane");
        assertThat(user.getLastName()).isEqualTo("Doe");
        assertThat(user.getStatus()).isEqualTo(UserStatus.ACTIVE);
        assertThat(user.getType()).isEqualTo(UserType.NATIVE);
        assertThat(user.isMfaEnabled()).isFalse();
        assertThat(user.isPasswordExpired()).isFalse();
        assertThat(user.getCreatedAt()).isEqualTo(NOW);
        assertThat(user.getUpdatedAt()).isEqualTo(NOW);
        assertThat(user.getMetadata()).containsExactlyEntriesOf(Map.of("dept", "audit"));
    }

    @Test
    void usernameAlreadyTakenInTheSpace_isRefused() {
        when(userRepository.findByUsername(SPACE, "jane")).thenReturn(Optional.of(mock(User.class)));

        assertThatThrownBy(() -> service.createNativeUser(command(), ORG_UUID, SPACE, ACCOUNT))
                .isInstanceOf(UserAlreadyExistsException.class)
                .hasMessageContaining("jane");

        verify(userRepository, never()).existsBySpaceAndAccount(SPACE, ACCOUNT);
    }

    @Test
    void accountAlreadyProfiledInTheSpace_isRefused() {
        when(userRepository.findByUsername(SPACE, "jane")).thenReturn(Optional.empty());
        when(userRepository.existsBySpaceAndAccount(SPACE, ACCOUNT)).thenReturn(true);

        assertThatThrownBy(() -> service.validateUserUniqueness(SPACE, "jane", ACCOUNT))
                .isInstanceOf(UserAlreadyExistsException.class)
                .hasMessageContaining("already has a profile");
    }
}
