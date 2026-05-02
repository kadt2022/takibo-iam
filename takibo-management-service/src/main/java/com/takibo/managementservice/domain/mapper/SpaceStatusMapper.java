package com.takibo.managementservice.domain.mapper;

import com.takibo.identitycore.domain.status.SpaceOperationalStatus;
import com.takibo.managementservice.domain.model.SpaceStatus;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface SpaceStatusMapper {
    SpaceOperationalStatus toCoreStatus(SpaceStatus status);
}
