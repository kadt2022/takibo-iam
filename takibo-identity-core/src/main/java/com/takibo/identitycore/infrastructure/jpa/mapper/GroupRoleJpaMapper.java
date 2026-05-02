package com.takibo.identitycore.infrastructure.jpa.mapper;

import com.takibo.identitycore.domain.model.GroupRole;
import com.takibo.identitycore.domain.vo.GroupId;
import com.takibo.identitycore.domain.vo.RoleId;
import com.takibo.identitycore.domain.vo.SpaceId;
import com.takibo.identitycore.infrastructure.entity.GroupRoleEntity;
import org.mapstruct.*;

import java.util.UUID;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface GroupRoleJpaMapper {

    // Domain -> Entity
    @Mapping(target = "id", source = "id")
    @Mapping(target = "orgId", ignore = true)
    @Mapping(target = "spaceId", source = "spaceId", qualifiedByName = "spaceIdToUuid")
    @Mapping(target = "groupId", source = "groupId", qualifiedByName = "groupIdToUuid")
    @Mapping(target = "roleId", source = "roleId", qualifiedByName = "roleIdToUuid")
    @Mapping(target = "assignedAt", source = "assignedAt")
    @Mapping(target = "assignedBy", source = "assignedBy")
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "version", source = "version")
    @Mapping(target = "group", ignore = true)
    @Mapping(target = "role", ignore = true)
    GroupRoleEntity toEntity(GroupRole link);

    // Entity -> Domain
    @Mapping(target = "id", source = "id")
    @Mapping(target = "spaceId", source = "spaceId", qualifiedByName = "uuidToSpaceId")
    @Mapping(target = "groupId", source = "groupId", qualifiedByName = "uuidToGroupId")
    @Mapping(target = "roleId", source = "roleId", qualifiedByName = "uuidToRoleId")
    @Mapping(target = "assignedAt", source = "assignedAt")
    @Mapping(target = "assignedBy", source = "assignedBy")
    @Mapping(target = "createdAt", source = "createdAt")
    @Mapping(target = "updatedAt", source = "updatedAt")
    @Mapping(target = "version", source = "version")
    GroupRole toDomain(GroupRoleEntity entity);

    // Helpers
    @Named("spaceIdToUuid")
    default UUID spaceIdToUuid(SpaceId id) {
        return (id == null) ? null : id.getValue();
    }

    @Named("uuidToSpaceId")
    default SpaceId uuidToSpaceId(UUID id) {
        return (id == null) ? null : SpaceId.of(id);
    }

    @Named("groupIdToUuid")
    default UUID groupIdToUuid(GroupId id) {
        return (id == null) ? null : id.getValue();
    }

    @Named("uuidToGroupId")
    default GroupId uuidToGroupId(UUID id) {
        return (id == null) ? null : GroupId.of(id);
    }

    @Named("roleIdToUuid")
    default UUID roleIdToUuid(RoleId id) {
        return (id == null) ? null : id.getValue();
    }

    @Named("uuidToRoleId")
    default RoleId uuidToRoleId(UUID id) {
        return (id == null) ? null : RoleId.of(id);
    }
}
