package com.takibo.identitycore.infrastructure.jpa.mapper;

import com.takibo.identitycore.domain.rbac.model.GroupAssignment;
import com.takibo.identitycore.infrastructure.entity.GroupAssignmentEntity;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;
import org.mapstruct.ReportingPolicy;

import java.util.UUID;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface GroupAssignmentMapper {

    // Domain -> Entity
    @Mapping(target = "id", source = "id")
    @Mapping(target = "orgId", source = "orgId")
    @Mapping(target = "spaceId", source = "spaceId")
    @Mapping(target = "identityType", source = "identityType")
    @Mapping(target = "identityId", source = "identityId")
    @Mapping(target = "groupCode", source = "groupCode")
    @Mapping(target = "businessGroupId", source = "businessGroupId")
    @Mapping(target = "groupSource", source = "groupSource")
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "createdBy", source = "createdBy", qualifiedByName = "stringToUuid")
    @Mapping(target = "updatedBy", source = "updatedBy", qualifiedByName = "stringToUuid")
    @Mapping(target = "identity", ignore = true)
    GroupAssignmentEntity toEntity(GroupAssignment assignment);

    // Entity -> Domain
    @Mapping(target = "id", source = "id")
    @Mapping(target = "orgId", source = "orgId")
    @Mapping(target = "spaceId", source = "spaceId")
    @Mapping(target = "identityType", ignore = true)
    @Mapping(target = "identity", ignore = true)
    @Mapping(target = "identityId", source = "identityId")
    @Mapping(target = "groupCode", source = "groupCode")
    @Mapping(target = "businessGroupId", source = "businessGroupId")
    @Mapping(target = "groupSource", source = "groupSource")
    @Mapping(target = "createdAt", source = "createdAt")
    @Mapping(target = "updatedAt", source = "updatedAt")
    @Mapping(target = "createdBy", source = "createdBy", qualifiedByName = "uuidToString")
    @Mapping(target = "updatedBy", source = "updatedBy", qualifiedByName = "uuidToString")
    GroupAssignment toDomain(GroupAssignmentEntity entity);

    @Named("uuidToString")
    default String uuidToString(UUID value) {
        return value == null ? null : value.toString();
    }

    @Named("stringToUuid")
    default UUID stringToUuid(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }

        String v = value.trim();
        if ("SYSTEM".equalsIgnoreCase(v)) {
            return null;
        }

        try {
            return UUID.fromString(v);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
