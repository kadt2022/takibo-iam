package com.takibo.identitycore.application.identity.port;

import com.takibo.identitycore.application.identity.readmodel.UserReadModel;
import com.takibo.identitycore.domain.status.UserStatus;
import com.takibo.identitycore.domain.type.UserType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Optional;
import java.util.UUID;

/** Port read-side : projections user + email, toujours scopées par space. */
public interface UserQueryRepository {

    Page<UserReadModel> findBySpace(UUID spaceId, UserStatus status, UserType type, String q, Pageable pageable);

    Optional<UserReadModel> findBySpaceAndId(UUID spaceId, UUID userId);
}
