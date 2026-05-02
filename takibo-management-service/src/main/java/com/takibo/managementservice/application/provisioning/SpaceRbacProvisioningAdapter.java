package com.takibo.managementservice.application.provisioning;

import com.takibo.managementservice.application.port.SpaceRbacProvisioningPort;
import com.takibo.managementservice.application.security.ActorSource;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Component
@RequiredArgsConstructor
public class SpaceRbacProvisioningAdapter implements SpaceRbacProvisioningPort {

    private final TechnicalRbacProvision technicalRbacProvision;

    @Transactional
    @Override
    public void provisionSpaceAdmin(UUID orgId, UUID spaceId, UUID creatorUserId, ActorSource source) {
        String actor = mapActor(source);
        technicalRbacProvision.provisionSpaceCreator(orgId, spaceId, creatorUserId, actor);
    }

    private String mapActor(ActorSource source) {
        return source == null ? "SYSTEM" : source.name();
    }
}
