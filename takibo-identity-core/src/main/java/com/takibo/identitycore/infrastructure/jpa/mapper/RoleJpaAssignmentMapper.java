package com.takibo.identitycore.infrastructure.jpa.mapper;

import com.takibo.identitycore.domain.model.Identity;
import com.takibo.identitycore.domain.model.IdentityType;
import com.takibo.identitycore.domain.rbac.model.RoleAssignment;
import com.takibo.identitycore.infrastructure.entity.RoleAssignmentEntity;
import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;
import org.mapstruct.ReportingPolicy;

import java.util.UUID;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface RoleJpaAssignmentMapper {

    // Entity -> Domain
    @BeanMapping(ignoreByDefault = true)
    @Mapping(target = "id", source = "id")
    @Mapping(target = "orgId", source = "orgId")
    @Mapping(target = "spaceId", source = "spaceId")
    @Mapping(target = "identity", source = "entity", qualifiedByName = "toIdentity")
    @Mapping(target = "roleCode", source = "roleCode")
    @Mapping(target = "businessRoleId", source = "businessRoleId")
    @Mapping(target = "roleSource", source = "assignmentSource")
    @Mapping(target = "createdAt", source = "createdAt")
    @Mapping(target = "updatedAt", source = "updatedAt")
    @Mapping(target = "createdBy", source = "createdBy", qualifiedByName = "uuidToString")
    @Mapping(target = "updatedBy", source = "updatedBy", qualifiedByName = "uuidToString")
    RoleAssignment toDomain(RoleAssignmentEntity entity);

    // Domain -> Entity
    @BeanMapping(ignoreByDefault = true)
    @Mapping(target = "id", source = "id")
    @Mapping(target = "orgId", source = "orgId")
    @Mapping(target = "spaceId", source = "spaceId")
    @Mapping(target = "identityType", source = "identity", qualifiedByName = "identityTypeToString")
    @Mapping(target = "identityId", source = "identity", qualifiedByName = "identityId")
    @Mapping(target = "roleCode", source = "roleCode")
    @Mapping(target = "businessRoleId", source = "businessRoleId")
    @Mapping(target = "assignmentSource", source = "roleSource")
    @Mapping(target = "createdBy", source = "createdBy", qualifiedByName = "stringToUuid")
    @Mapping(target = "updatedBy", source = "updatedBy", qualifiedByName = "stringToUuid")
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "identity", ignore = true)
    RoleAssignmentEntity toEntity(RoleAssignment assignment);

    // ----------------------------
    // Helpers
    // ----------------------------

    @Named("toIdentity")
    default Identity toIdentity(RoleAssignmentEntity entity) {
        if (entity == null || entity.getIdentityType() == null || entity.getIdentityId() == null) {
            return null;
        }
        IdentityType type = stringToIdentityType(entity.getIdentityType());
        return type == null ? null : new Identity(type, entity.getIdentityId());
    }

    @Named("identityTypeToString")
    default String identityTypeToString(Identity identity) {
        return (identity == null || identity.type() == null) ? null : identity.type().name();
    }

    @Named("identityId")
    default UUID identityId(Identity identity) {
        return identity == null ? null : identity.id();
    }

    default IdentityType stringToIdentityType(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return IdentityType.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

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
