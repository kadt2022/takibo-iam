package com.takibo.identitycore.domain.model;

import com.takibo.identitycore.domain.vo.SpaceId;

import java.util.UUID;

public record SpaceContext(SpaceId spaceId, UUID organizationId) {
}
