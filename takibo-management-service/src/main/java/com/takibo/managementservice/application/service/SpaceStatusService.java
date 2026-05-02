package com.takibo.managementservice.application.service;

import com.takibo.managementservice.domain.model.SpaceStatus;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface SpaceStatusService {
    SpaceStatus updateStatus(UUID spaceId, SpaceStatus newStatus, Optional<String> reason);
    Optional<SpaceStatus> findStatus(UUID spaceId);
    Optional<Instant> lastStatusUpdateAt(UUID spaceId);
}
