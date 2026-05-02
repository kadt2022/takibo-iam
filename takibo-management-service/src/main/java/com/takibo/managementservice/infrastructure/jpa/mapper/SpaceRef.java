package com.takibo.managementservice.infrastructure.jpa.mapper;

import com.takibo.managementservice.infrastructure.entity.SpaceEntity;
import java.util.UUID;

@FunctionalInterface
public interface SpaceRef {
    SpaceEntity getReference(UUID id);
}
