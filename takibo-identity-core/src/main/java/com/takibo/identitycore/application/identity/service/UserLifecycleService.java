package com.takibo.identitycore.application.identity.service;

import com.takibo.identitycore.application.identity.command.ChangeUserStatusCommand;
import com.takibo.identitycore.application.identity.command.UpdateUserProfileCommand;
import com.takibo.identitycore.application.identity.mapper.UserMapper;
import com.takibo.identitycore.application.identity.port.UserLifecycleCase;
import com.takibo.identitycore.domain.exception.UserAlreadyExistsException;
import com.takibo.identitycore.domain.exception.UserCreationException;
import com.takibo.identitycore.domain.exception.UserNotFoundException;
import com.takibo.identitycore.domain.model.Account;
import com.takibo.identitycore.domain.model.User;
import com.takibo.identitycore.domain.repository.AccountRepository;
import com.takibo.identitycore.domain.repository.UserRepository;
import com.takibo.identitycore.domain.vo.SpaceId;
import com.takibo.identitycore.domain.vo.UserId;
import com.takibo.identitycore.integration.security.SpaceBoundaryGuard;
import com.takibo.identitycore.integration.space.SpaceContextVerifier;
import com.takibo.identitycore.integration.space.port.ResolvedSpaceKey;
import com.takibo.identitycore.interfaces.rest.response.UserResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Cycle de vie du user local : profil et statut. Le domaine décide (les ancres
 * d'identité sont intouchables, {@code User.changeStatus} valide la transition) ;
 * ce service orchestre. Un user hors du space courant n'existe pas -> 404.
 * <p>
 * Le {@code reason} des actions de statut est journalisé (audit-only) — pas de
 * colonne dédiée en PR #24.
 */
@Service
@RequiredArgsConstructor
@Transactional
@Slf4j
public class UserLifecycleService implements UserLifecycleCase {

    private final SpaceContextVerifier spaceContextVerifier;
    private final SpaceBoundaryGuard spaceBoundaryGuard;
    private final UserRepository userRepository;
    private final AccountRepository accountRepository;
    private final UserMapper userMapper;

    @Override
    public UserResponse updateProfile(ResolvedSpaceKey key, UpdateUserProfileCommand command) {
        guard(key);
        User user = requireUserInSpace(key, command.userId());

        // PATCH : un champ null = inchangé.
        String username = command.username() != null ? command.username() : user.getUsername();

        if (!username.equalsIgnoreCase(user.getUsername())
                && userRepository.existsByUsernameIgnoreCaseAndIdNot(
                        new SpaceId(key.spaceId()), username, user.getId().value())) {
            throw new UserAlreadyExistsException("Username already exists in this space: " + username);
        }

        User updated = user.updateProfile(
                username,
                command.firstName() != null ? command.firstName() : user.getFirstName(),
                command.lastName() != null ? command.lastName() : user.getLastName(),
                command.metadata() != null ? command.metadata() : user.getMetadata());

        return toResponse(userRepository.save(updated));
    }

    @Override
    public UserResponse changeStatus(ResolvedSpaceKey key, ChangeUserStatusCommand command) {
        guard(key);
        User user = requireUserInSpace(key, command.userId());

        // Le domaine refuse les transitions illégitimes (InvalidStatusTransitionException -> 409).
        User updated = user.changeStatus(command.targetStatus());
        User saved = userRepository.save(updated);

        log.info("User status changed userId={} spaceId={} {} -> {} reason={}",
                user.getId().value(), key.spaceId(), user.getStatus(), command.targetStatus(),
                command.reason() != null ? command.reason() : "-");

        return toResponse(saved);
    }

    private void guard(ResolvedSpaceKey key) {
        spaceContextVerifier.validateSpaceContext(key.spaceId());
        spaceBoundaryGuard.assertTokenMatches(key);
    }

    /** Anti-énumération : absent du space courant == inexistant, même s'il vit ailleurs. */
    private User requireUserInSpace(ResolvedSpaceKey key, UUID userId) {
        return userRepository.findById(new UserId(userId))
                .filter(user -> user.getSpaceId().value().equals(key.spaceId()))
                .orElseThrow(() -> new UserNotFoundException("User not found in this space: " + userId));
    }

    private UserResponse toResponse(User user) {
        Account account = accountRepository.findById(user.getAccountId())
                .orElseThrow(() -> new UserCreationException(
                        "Account not found for user " + user.getId().value()));
        return userMapper.toUserResponse(user, account.getEmail());
    }
}
