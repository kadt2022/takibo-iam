package com.takibo.managementservice.domain.mapper;

import com.takibo.managementservice.domain.model.Space;
import com.takibo.managementservice.domain.vo.SpaceId;
import com.takibo.managementservice.interfaces.rest.response.SpaceResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;
import org.mapstruct.ReportingPolicy;

import java.util.UUID;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface SpaceMapper {

    @Mapping(target = "id", source = "id", qualifiedByName = "spaceIdToUuid")
    SpaceResponse toSpaceResponse(Space space);

    @Named("spaceIdToUuid")
    default UUID spaceIdToUuid(SpaceId id) {
        return id == null ? null : id.value();
    }
}
