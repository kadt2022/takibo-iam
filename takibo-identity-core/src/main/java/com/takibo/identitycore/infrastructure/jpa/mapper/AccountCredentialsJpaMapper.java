package com.takibo.identitycore.infrastructure.jpa.mapper;

import com.takibo.identitycore.domain.model.AccountCredentials;
import com.takibo.identitycore.domain.vo.AccountId;
import com.takibo.identitycore.domain.vo.PasswordHash;
import com.takibo.identitycore.infrastructure.entity.AccountCredentialsEntity;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;
import org.mapstruct.ReportingPolicy;

import java.util.UUID;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface AccountCredentialsJpaMapper {

    // ===== Domain → Entity =====

    @Mapping(target = "orgId", source = "orgId")
    @Mapping(target = "accountId", source = "source.accountId", qualifiedByName = "accountIdToUuid")
    @Mapping(target = "passwordHash", source = "source.passwordHash", qualifiedByName = "passwordHashToString")
    @Mapping(target = "passwordAlgo", source = "source.passwordHash.algo")
    @Mapping(target = "passwordVersion", source = "source.passwordHash.version")
    @Mapping(target = "passwordUpdatedAt", source = "source.passwordUpdatedAt")
    @Mapping(target = "mustChangeNextLogin", source = "source.mustChangeNextLogin")
    @Mapping(target = "failedAttempts", source = "source.failedAttempts")
    @Mapping(target = "lockedUntil", source = "source.lockedUntil")
    @Mapping(target = "createdAt", ignore = true)  // @PrePersist
    @Mapping(target = "updatedAt", ignore = true)  // @PrePersist
    @Mapping(target = "version", ignore = true)    //  CORRECTION: Laisser Hibernate gérer la version
    @Mapping(target = "isNew", constant = "true")  //  CORRECTION: Marquer comme nouvelle entité
    @Mapping(target = "account", ignore = true)    //  IGNORER account (géré manuellement)
    AccountCredentialsEntity toEntity(AccountCredentials source, UUID orgId);

    // ===== Entity → Domain =====

    @Mapping(target = "accountId", source = "accountId", qualifiedByName = "uuidToAccountId")
    @Mapping(target = "passwordHash", source = "entity", qualifiedByName = "toPasswordHash")
    @Mapping(target = "passwordUpdatedAt", source = "passwordUpdatedAt")
    @Mapping(target = "mustChangeNextLogin", source = "mustChangeNextLogin")
    @Mapping(target = "failedAttempts", source = "failedAttempts")
    @Mapping(target = "lockedUntil", source = "lockedUntil")
    @Mapping(target = "createdAt", source = "createdAt")
    @Mapping(target = "updatedAt", source = "updatedAt")
    @Mapping(target = "version", source = "version")
    @Mapping(target = "clock", ignore = true)
    @Mapping(target = "account", ignore = true)  //  IGNORER account (géré manuellement)
    AccountCredentials toDomain(AccountCredentialsEntity entity);

    // ===== HELPERS =====

    @Named("accountIdToUuid")
    default UUID accountIdToUuid(AccountId id) {
        return (id == null) ? null : id.getValue();
    }

    @Named("uuidToAccountId")
    default AccountId uuidToAccountId(UUID id) {
        return (id == null) ? null : AccountId.of(id);
    }

    @Named("passwordHashToString")
    default String passwordHashToString(PasswordHash hash) {
        return (hash == null) ? null : hash.getHash();
    }

    @Named("toPasswordHash")
    default PasswordHash toPasswordHash(AccountCredentialsEntity entity) {
        if (entity == null || entity.getPasswordHash() == null) {
            return null;
        }
        return PasswordHash.of(
                entity.getPasswordHash(),
                entity.getPasswordAlgo(),
                entity.getPasswordVersion()
        );
    }
}