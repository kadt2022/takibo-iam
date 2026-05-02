package com.takibo.identitycore.infrastructure.jpa.mapper;

import com.takibo.identitycore.domain.model.Role;
import com.takibo.identitycore.domain.vo.RoleId;
import com.takibo.identitycore.domain.vo.SpaceId;
import com.takibo.identitycore.infrastructure.entity.RoleEntity;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;
import org.mapstruct.ReportingPolicy;

import java.util.UUID;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface RoleJpaMapper {

    // Domain -> Entity
    @Mapping(target = "id", source = "id", qualifiedByName = "roleIdToUuid")
    @Mapping(target = "spaceId", source = "spaceId", qualifiedByName = "spaceIdToUuid")
    @Mapping(target = "orgId", ignore = true)
    @Mapping(target = "code", source = "code")
    @Mapping(target = "name", source = "name")
    @Mapping(target = "description", source = "description")
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "version", source = "version")
    RoleEntity toEntity(Role role);

    // Entity -> Domain
    @Mapping(target = "id", source = "id", qualifiedByName = "uuidToRoleId")
    @Mapping(target = "spaceId", source = "spaceId", qualifiedByName = "uuidToSpaceId")
    @Mapping(target = "code", source = "code")
    @Mapping(target = "name", source = "name")
    @Mapping(target = "description", source = "description")
    @Mapping(target = "createdAt", source = "createdAt")
    @Mapping(target = "updatedAt", source = "updatedAt")
    @Mapping(target = "version", source = "version")
    Role toDomain(RoleEntity entity);

    // Helpers
    @Named("roleIdToUuid")
    default UUID roleIdToUuid(RoleId id) {
        return (id == null) ? null : id.getValue();
    }

    @Named("uuidToRoleId")
    default RoleId uuidToRoleId(UUID id) {
        return (id == null) ? null : RoleId.of(id);
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
