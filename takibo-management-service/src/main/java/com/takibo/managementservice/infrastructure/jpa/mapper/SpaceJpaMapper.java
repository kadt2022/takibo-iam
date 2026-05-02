package com.takibo.managementservice.infrastructure.jpa.mapper;

import com.takibo.managementservice.domain.model.Space;
import com.takibo.managementservice.domain.vo.SpaceId;
import com.takibo.managementservice.infrastructure.entity.SpaceEntity;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;
import org.mapstruct.ReportingPolicy;

import java.util.UUID;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface SpaceJpaMapper {

    // ===== Domain → Entity =====

    @Mapping(target = "id",              source = "id", qualifiedByName = "spaceIdToUuid")
    @Mapping(target = "orgId",           source = "orgId")
    @Mapping(target = "ownerAccountId",  source = "ownerAccountId")
    @Mapping(target = "code",            source = "code")
    @Mapping(target = "name",            source = "name")
    @Mapping(target = "description",     source = "description")
    @Mapping(target = "status",          source = "status")
    @Mapping(target = "statusReason",    source = "statusReason")
    @Mapping(target = "statusUpdatedAt", source = "statusUpdatedAt")
    @Mapping(target = "createdAt",       ignore = true)  // @PrePersist
    @Mapping(target = "updatedAt",       ignore = true)  // @PrePersist
    @Mapping(target = "version",         ignore = true)
    @Mapping(target = "organization",    ignore = true)  // ✅ Relation JPA optionnelle
    @Mapping(target = "domains",         ignore = true)  // ✅ OneToMany - géré séparément
    @Mapping(target = "oAuth2Clients",   ignore = true)  // ✅ OneToMany - géré séparément
    SpaceEntity toEntity(Space space);

    // ===== Entity → Domain =====

    @Mapping(target = "id",              source = "id", qualifiedByName = "uuidToSpaceId")
    @Mapping(target = "orgId",           source = "orgId")
    @Mapping(target = "ownerAccountId",  source = "ownerAccountId")
    @Mapping(target = "code",            source = "code")
    @Mapping(target = "name",            source = "name")
    @Mapping(target = "description",     source = "description")
    @Mapping(target = "status",          source = "status")
    @Mapping(target = "statusReason",    source = "statusReason")
    @Mapping(target = "statusUpdatedAt", source = "statusUpdatedAt")
    @Mapping(target = "createdAt",       source = "createdAt")
    @Mapping(target = "updatedAt",       source = "updatedAt")
    @Mapping(target = "version",         source = "version")
    Space toDomain(SpaceEntity entity);

    // ===== HELPERS =====

    @Named("spaceIdToUuid")
    default UUID spaceIdToUuid(SpaceId id) {
        return id == null ? null : id.value();
    }

    @Named("uuidToSpaceId")
    default SpaceId uuidToSpaceId(UUID id) {
        return id == null ? null : SpaceId.of(id);
    }
}