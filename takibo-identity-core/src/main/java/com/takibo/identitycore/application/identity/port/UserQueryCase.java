package com.takibo.identitycore.application.identity.port;

import com.takibo.identitycore.domain.status.UserStatus;
import com.takibo.identitycore.domain.type.UserType;
import com.takibo.identitycore.integration.space.port.ResolvedSpaceKey;
import com.takibo.identitycore.interfaces.rest.response.UserPageResponse;
import com.takibo.identitycore.interfaces.rest.response.UserResponse;

import java.util.UUID;

/** Port in : lecture des users d'un space (frontière stricte du token exigée). */
public interface UserQueryCase {

    UserPageResponse listUsers(ResolvedSpaceKey key, UserStatus status, UserType type, String q, int page, int size);

    UserResponse getUser(ResolvedSpaceKey key, UUID userId);
}
