package com.takibo.managementservice.application.port;



import com.takibo.managementservice.domain.model.ActorSource;

import java.util.UUID;

public interface SpaceRbacProvisioningPort {
    void provisionSpaceAdmin(UUID orgId, UUID spaceId, UUID creatorUserId, ActorSource source);
}
