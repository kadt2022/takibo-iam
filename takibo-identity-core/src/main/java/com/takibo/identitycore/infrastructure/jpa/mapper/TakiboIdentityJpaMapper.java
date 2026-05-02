package com.takibo.identitycore.infrastructure.jpa.mapper;

import com.takibo.identitycore.domain.model.TakiboIdentity;
import com.takibo.identitycore.domain.vo.AccountId;
import com.takibo.identitycore.domain.vo.OrganizationId;
import com.takibo.identitycore.domain.vo.TakiboIdentityId;
import com.takibo.identitycore.infrastructure.entity.TakiboIdentityEntity;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;
import org.mapstruct.ReportingPolicy;

import java.util.UUID;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface TakiboIdentityJpaMapper {

    // ===== Domain → Entity =====

    @Mapping(target = "identityId", source = "id", qualifiedByName = "identityIdToUuid")
    @Mapping(target = "orgId", source = "orgId", qualifiedByName = "orgIdToUuid")
    @Mapping(target = "accountId", source = "accountId", qualifiedByName = "accountIdToUuid")
    TakiboIdentityEntity toEntity(TakiboIdentity domain);

    // ===== Entity → Domain =====

    @Mapping(target = "id", source = "identityId", qualifiedByName = "uuidToIdentityId")
    @Mapping(target = "orgId", source = "orgId", qualifiedByName = "uuidToOrgId")
    @Mapping(target = "accountId", source = "accountId", qualifiedByName = "uuidToAccountId")
    TakiboIdentity toDomain(TakiboIdentityEntity entity);

    // ===== HELPERS =====

    @Named("identityIdToUuid")
    default UUID identityIdToUuid(TakiboIdentityId id) {
        return id == null ? null : id.getValue();
    }

    @Named("uuidToIdentityId")
    default TakiboIdentityId uuidToIdentityId(UUID id) {
        return id == null ? null : TakiboIdentityId.of(id);
    }

    @Named("orgIdToUuid")
    default UUID orgIdToUuid(OrganizationId id) {
        return id == null ? null : id.getValue();
    }

    @Named("uuidToOrgId")
    default OrganizationId uuidToOrgId(UUID id) {
        return id == null ? null : OrganizationId.of(id);
    }

    @Named("accountIdToUuid")
    default UUID accountIdToUuid(AccountId id) {
        return id == null ? null : id.getValue();
    }

    @Named("uuidToAccountId")
    default AccountId uuidToAccountId(UUID id) {
        return id == null ? null : AccountId.of(id);
    }
}