package com.takibo.managementservice.application.service;

import com.takibo.managementservice.domain.model.SpaceStatus;
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
public class SpaceStatusServiceImpl implements SpaceStatusService {

    private final SpaceRepository spaceRepository;

    @Override
    public SpaceStatus updateStatus(UUID spaceId, SpaceStatus newStatus, Optional<String> reason) {
        // Idempotence : si même statut, ne rien écrire
        SpaceStatus current = spaceRepository.findStatusById(spaceId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown port " + spaceId));
        if (current == newStatus) {
            return current;
        }
        int updated = spaceRepository.updateStatus(
                spaceId, newStatus, reason.orElse(null), Instant.now());
        if (updated == 0) {
            throw new IllegalStateException("Concurrent update prevented updating status for space " + spaceId);
        }
        return newStatus;
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
