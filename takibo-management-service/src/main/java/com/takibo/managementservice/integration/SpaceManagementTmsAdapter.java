package com.takibo.managementservice.integration;

import com.takibo.identitycore.domain.vo.SpaceId;
import com.takibo.identitycore.integration.space.port.SpaceManagementCase;
import com.takibo.managementservice.application.service.SpaceQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class SpaceManagementTmsAdapter implements SpaceManagementCase {

    private final SpaceQueryService spaces;

    @Override
    public boolean doesSpaceExist(SpaceId spaceId) {
        return spaces.exists(spaceId.value());
    }

    @Override
    public Optional<UUID> findOrgIdBySpaceId(SpaceId spaceId) {
        return spaces.findOrganizationId(spaceId.value());
    }
}
