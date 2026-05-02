package com.takibo.managementservice.interfaces.rest.response;

import java.util.UUID;

public record SpaceSignupResponse(
        UUID spaceId,
        String spaceCode,
        String spaceName,
        UUID accountId,
        UUID userId,
        String assignedRole,
        String assignedGroup
) {}
