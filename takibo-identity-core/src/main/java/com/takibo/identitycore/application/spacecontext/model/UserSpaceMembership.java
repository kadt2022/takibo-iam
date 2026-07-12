package com.takibo.identitycore.application.spacecontext.model;

import com.takibo.identitycore.domain.status.UserStatus;

import java.util.UUID;

public record UserSpaceMembership(
        UUID spaceId,
        UUID userId,
        UserStatus userStatus
) {
}
