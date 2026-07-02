package com.takibo.identitycore.application.identity.service;

import com.takibo.identitycore.application.identity.mapper.UserMapper;
import com.takibo.identitycore.application.identity.port.UserQueryCase;
import com.takibo.identitycore.application.identity.port.UserQueryRepository;
import com.takibo.identitycore.application.identity.readmodel.UserReadModel;
import com.takibo.identitycore.domain.exception.UserNotFoundException;
import com.takibo.identitycore.domain.status.UserStatus;
import com.takibo.identitycore.domain.type.UserType;
import com.takibo.identitycore.integration.security.SpaceBoundaryGuard;
import com.takibo.identitycore.integration.space.SpaceContextVerifier;
import com.takibo.identitycore.integration.space.port.ResolvedSpaceKey;
import com.takibo.identitycore.interfaces.rest.response.UserPageResponse;
import com.takibo.identitycore.interfaces.rest.response.UserResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Read-side des users d'un space. Frontière stricte : le token doit être situé
 * sur l'org/space résolus. Un user hors du space courant N'EXISTE PAS (404) —
 * jamais de 403 qui confirmerait son existence ailleurs.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserQueryService implements UserQueryCase {

    private static final int MAX_PAGE_SIZE = 100;

    private final SpaceContextVerifier spaceContextVerifier;
    private final SpaceBoundaryGuard spaceBoundaryGuard;
    private final UserQueryRepository userQueryRepository;
    private final UserMapper userMapper;

    @Override
    public UserPageResponse listUsers(ResolvedSpaceKey key, UserStatus status, UserType type, String q,
                                      int page, int size) {
        guard(key);

        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);

        Page<UserReadModel> result = userQueryRepository.findBySpace(
                key.spaceId(), status, type, q,
                PageRequest.of(safePage, safeSize, Sort.by(Sort.Direction.ASC, "username")));

        return new UserPageResponse(
                result.getContent().stream().map(userMapper::toUserResponse).toList(),
                result.getNumber(),
                result.getSize(),
                result.getTotalElements(),
                result.getTotalPages());
    }

    @Override
    public UserResponse getUser(ResolvedSpaceKey key, UUID userId) {
        guard(key);

        return userQueryRepository.findBySpaceAndId(key.spaceId(), userId)
                .map(userMapper::toUserResponse)
                .orElseThrow(() -> new UserNotFoundException("User not found in this space: " + userId));
    }

    private void guard(ResolvedSpaceKey key) {
        spaceContextVerifier.validateSpaceContext(key.spaceId());
        spaceBoundaryGuard.assertTokenMatches(key);
    }
}
