package com.takibo.identitycore.interfaces.rest.response;

import com.takibo.identitycore.domain.status.UserStatus;

import java.util.UUID;

public record CurrentUserSpaceItemResponse(
        UUID spaceId,
        String code,
        String name,
        UUID userId,
        String spaceStatus,
        UserStatus userStatus,
        boolean selectable
) {
}
