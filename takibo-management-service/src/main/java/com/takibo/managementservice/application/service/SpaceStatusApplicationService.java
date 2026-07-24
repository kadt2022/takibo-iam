package com.takibo.managementservice.application.service;

import com.takibo.managementservice.domain.model.SpaceStatus;
import com.takibo.managementservice.domain.policy.SpaceStatusTransitionPolicy;
import com.takibo.managementservice.domain.repository.SpaceRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Service
@Transactional
@RequiredArgsConstructor
public class SpaceStatusApplicationService implements SpaceStatusService {

    private final SpaceRepository spaceRepository;
    private final SpaceStatusTransitionPolicy statusTransitionPolicy;

    @Override
    public SpaceStatus updateStatus(
            UUID spaceId,
            SpaceStatus newStatus,
            Optional<String> reason
    ) {
        SpaceStatus currentStatus = spaceRepository.findStatusById(spaceId)
                .orElseThrow(() ->
                        new IllegalArgumentException("Unknown port " + spaceId)
                );

        return statusTransitionPolicy
                .resolveTransition(currentStatus, newStatus)
                .map(transition -> persistTransition(
                        spaceId,
                        transition.requested(),
                        reason
                ))
                .orElse(currentStatus);
    }

    private SpaceStatus persistTransition(
            UUID spaceId,
            SpaceStatus status,
            Optional<String> reason
    ) {
        int updated = spaceRepository.updateStatus(
                spaceId,
                status,
                reason.orElse(null),
                Instant.now()
        );

        return Optional.of(updated)
                .filter(count -> count > 0)
                .map(ignored -> status)
                .orElseThrow(() -> new IllegalStateException(
                        "Concurrent update prevented updating status for space "
                                + spaceId
                ));
    }

    @Override
    public Optional<SpaceStatus> findStatus(UUID spaceId) {
        return spaceRepository.findStatusById(spaceId);
    }

    @Override
    public Optional<Instant> lastStatusUpdateAt(UUID spaceId) {
        return spaceRepository.findStatusUpdatedAtById(spaceId);
    }
}
