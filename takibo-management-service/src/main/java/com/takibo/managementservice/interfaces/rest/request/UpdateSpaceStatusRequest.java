package com.takibo.managementservice.interfaces.rest.request;

import com.takibo.managementservice.domain.model.SpaceStatus;

public record UpdateSpaceStatusRequest(
        SpaceStatus status,
        String reason
) {}
