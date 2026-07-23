package com.takibo.managementservice.infrastructure.adapter;

import com.takibo.managementservice.application.port.SpaceLookupPort;
import com.takibo.managementservice.infrastructure.jpa.repository.JpaSpaceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class SpaceLookupAdapter implements SpaceLookupPort {

    private final JpaSpaceRepository spaces;

    @Override
    public boolean existsById(UUID spaceId) {
        return spaces.existsById(spaceId);
    }

    @Override
    public Optional<UUID> findOrganizationId(UUID spaceId) {
        return spaces.findOrgIdById(spaceId);
    }
}
