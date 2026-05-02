package com.takibo.identitycore.infrastructure.jpa.mapper;

import com.takibo.identitycore.domain.model.Account;
import com.takibo.identitycore.domain.vo.AccountId;
import com.takibo.identitycore.domain.model.EmailAddress;
import com.takibo.identitycore.infrastructure.entity.AccountEntity;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;
import org.mapstruct.ReportingPolicy;

import java.util.UUID;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface AccountJpaMapper {

    // Domain -> Entity
    @Mapping(target = "id", source = "id", qualifiedByName = "accountIdToUuid")
    @Mapping(target = "orgId", source = "orgId")
    @Mapping(target = "email", source = "email", qualifiedByName = "emailToString")
    @Mapping(target = "displayName", source = "displayName")
    @Mapping(target = "avatarUrl", source = "avatarUrl")
    @Mapping(target = "metadata", source = "metadata")
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "version", ignore = true)
    @Mapping(target = "isNew", ignore = true)
    AccountEntity toEntity(Account account);

    // Entity -> Domain
    @Mapping(target = "id", source = "id", qualifiedByName = "uuidToAccountId")
    @Mapping(target = "orgId", source = "orgId")
    @Mapping(target = "email", source = "email", qualifiedByName = "stringToEmail")
    @Mapping(target = "displayName", source = "displayName")
    @Mapping(target = "avatarUrl", source = "avatarUrl")
    @Mapping(target = "metadata", source = "metadata")
    @Mapping(target = "createdAt", source = "createdAt")
    @Mapping(target = "updatedAt", source = "updatedAt")
    @Mapping(target = "version", source = "version")
    Account toDomain(AccountEntity entity);

    // Helpers
    @Named("accountIdToUuid")
    default UUID accountIdToUuid(AccountId id) {
        return (id == null) ? null : id.getValue();
    }

    @Named("uuidToAccountId")
    default AccountId uuidToAccountId(UUID id) {
        return (id == null) ? null : AccountId.of(id);
    }

    @Named("emailToString")
    default String emailToString(EmailAddress email) {
        return (email == null) ? null : email.value();
    }

    @Named("stringToEmail")
    default EmailAddress stringToEmail(String email) {
        return (email == null) ? null : new EmailAddress(email);
    }
}
