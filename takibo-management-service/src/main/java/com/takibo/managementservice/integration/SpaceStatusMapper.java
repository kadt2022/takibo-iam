package com.takibo.managementservice.integration;

import com.takibo.identitycore.domain.status.SpaceOperationalStatus;
import com.takibo.managementservice.domain.model.SpaceStatus;
import org.springframework.stereotype.Component;

@Component
public class SpaceStatusMapper {

    public SpaceOperationalStatus toCoreStatus(SpaceStatus status) {
        return SpaceOperationalStatus.valueOf(status.name());
    }
}
