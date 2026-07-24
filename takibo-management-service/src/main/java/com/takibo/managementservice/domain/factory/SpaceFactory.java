package com.takibo.managementservice.domain.factory;

import com.takibo.managementservice.domain.model.Space;
import com.takibo.managementservice.domain.model.SpaceCreationRequest;

public final class SpaceFactory {

    public Space createSpace(SpaceCreationRequest request) {
        return Space.createNew(
                request.spaceId(),
                request.organization().orgId(),
                request.ownerAccountId(),
                request.code(),
                request.name(),
                request.description(),
                request.createdAt()
        );
    }
}
