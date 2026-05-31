package com.takibo.identitycore.domain.service;

import com.takibo.identitycore.application.identity.command.CreateUserCommand;
import com.takibo.identitycore.domain.exception.UserAlreadyExistsException;
import com.takibo.identitycore.domain.model.User;
import com.takibo.identitycore.domain.repository.UserRepository;
import com.takibo.identitycore.domain.status.UserStatus;
import com.takibo.identitycore.domain.vo.AccountId;
import com.takibo.identitycore.domain.vo.SpaceId;
import com.takibo.identitycore.domain.vo.UserId;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UserDomainService {

    private final UserRepository userRepository;
    private final Clock clock;

    public User createNativeUser(CreateUserCommand command, UUID orgId, SpaceId spaceId, AccountId accountId) {
        // Règles métier d’unicité
        validateUserUniqueness(spaceId, command.username(), accountId);

        // Construction de l’agrégat
        return User.createNative(
                UserId.generate(),
                orgId,
                spaceId,
                accountId,
                command.username(),
                command.firstName(),
                command.lastName(),
                UserStatus.ACTIVE,
                false,
                false,
                null,
                clock.instant(),
                clock.instant(),
                command.metadata()
        );
    }

    public void validateUserUniqueness(SpaceId spaceId, String username, AccountId accountId) {
        validateUsernameAvailability(spaceId, username);
        validateAccountNotInSpace(spaceId, accountId);
    }

    private void validateUsernameAvailability(SpaceId spaceId, String username) {
        if (userRepository.findByUsername(spaceId, username).isPresent()) {
            throw new UserAlreadyExistsException("Username '" + username + "' already exists in this port");
        }
    }

    private void validateAccountNotInSpace(SpaceId spaceId, AccountId accountId) {
        if (userRepository.existsBySpaceAndAccount(spaceId, accountId)) {
            throw new UserAlreadyExistsException("Account already has a profile in this port");
        }
    }
}
