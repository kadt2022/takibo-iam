package com.takibo.managementservice.domain.policy;

import com.takibo.managementservice.domain.model.SpaceStatus;
import com.takibo.managementservice.domain.model.SpaceStatusTransition;

import java.util.Optional;

public final class SpaceStatusTransitionPolicy {

    public Optional<SpaceStatusTransition> resolveTransition(
            SpaceStatus currentStatus,
            SpaceStatus requestedStatus
    ) {
        return Optional.of(
                        new SpaceStatusTransition(
                                currentStatus,
                                requestedStatus
                        )
                )
                .filter(SpaceStatusTransition::changesStatus);
    }
}
