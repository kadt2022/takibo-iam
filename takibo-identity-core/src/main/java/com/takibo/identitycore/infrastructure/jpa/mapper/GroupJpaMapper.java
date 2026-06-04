package com.takibo.identitycore.infrastructure.jpa.mapper;

import com.takibo.identitycore.domain.model.Group;
import com.takibo.identitycore.domain.vo.GroupId;
import com.takibo.identitycore.domain.vo.SpaceId;
import com.takibo.identitycore.infrastructure.entity.GroupEntity;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;
import org.mapstruct.ReportingPolicy;

import java.util.UUID;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface GroupJpaMapper {

    // Domain -> Entity
    @Mapping(target = "id", source = "id", qualifiedByName = "groupIdToUuid")
    @Mapping(target = "spaceId", source = "spaceId", qualifiedByName = "spaceIdToUuid")
    @Mapping(target = "orgId", ignore = true)
    @Mapping(target = "nature", source = "nature")
    @Mapping(target = "code", source = "code")
    @Mapping(target = "name", source = "name")
    @Mapping(target = "description", source = "description")
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "version", source = "version")
    GroupEntity toEntity(Group group);

    // Entity -> Domain
    @Mapping(target = "id", source = "id", qualifiedByName = "uuidToGroupId")
    @Mapping(target = "spaceId", source = "spaceId", qualifiedByName = "uuidToSpaceId")
    @Mapping(target = "nature", source = "nature")
    @Mapping(target = "code", source = "code")
    @Mapping(target = "name", source = "name")
    @Mapping(target = "description", source = "description")
    @Mapping(target = "createdAt", source = "createdAt")
    @Mapping(target = "updatedAt", source = "updatedAt")
    @Mapping(target = "version", source = "version")
    Group toDomain(GroupEntity entity);

    // Helpers
    @Named("groupIdToUuid")
    default UUID groupIdToUuid(GroupId id) {
        return (id == null) ? null : id.getValue();
    }

    @Named("uuidToGroupId")
    default GroupId uuidToGroupId(UUID id) {
        return (id == null) ? null : GroupId.of(id);
    }

    @Named("spaceIdToUuid")
    default UUID spaceIdToUuid(SpaceId id) {
        return (id == null) ? null : id.getValue();
    }

    @Named("uuidToSpaceId")
    default SpaceId uuidToSpaceId(UUID id) {
        return (id == null) ? null : SpaceId.of(id);
    }
}
