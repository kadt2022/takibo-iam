package com.takibo.identitycore.infrastructure.adapter;

import com.takibo.identitycore.application.identity.port.UserQueryRepository;
import com.takibo.identitycore.application.identity.readmodel.UserReadModel;
import com.takibo.identitycore.domain.status.UserStatus;
import com.takibo.identitycore.domain.type.UserType;
import com.takibo.identitycore.infrastructure.jpa.repository.JpaUserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserQueryRepositoryAdapter implements UserQueryRepository {

    private final JpaUserRepository jpa;

    @Override
    public Page<UserReadModel> findBySpace(UUID spaceId, UserStatus status, UserType type, String q, Pageable pageable) {
        String normalizedQ = (q == null || q.isBlank()) ? null : q.trim();
        return jpa.findReadModelsBySpace(spaceId, status, type, normalizedQ, pageable);
    }

    @Override
    public Optional<UserReadModel> findBySpaceAndId(UUID spaceId, UUID userId) {
        return jpa.findReadModelBySpaceAndId(spaceId, userId);
    }
}
