package com.takibo.identitycore.infrastructure.jpa.mapper;

import com.takibo.identitycore.domain.model.User;
import com.takibo.identitycore.domain.vo.AccountId;
import com.takibo.identitycore.domain.vo.SpaceId;
import com.takibo.identitycore.domain.vo.UserId;
import com.takibo.identitycore.infrastructure.entity.UserEntity;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;
import org.mapstruct.ReportingPolicy;

import java.util.UUID;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface UserJpaMapper {

    @Mapping(target = "id", source = "id", qualifiedByName = "userIdToUuid")
    @Mapping(target = "spaceId", source = "spaceId", qualifiedByName = "spaceIdToUuid")
    @Mapping(target = "orgId", ignore = true)
    @Mapping(target = "accountId", source = "accountId", qualifiedByName = "accountIdToUuid")
    @Mapping(target = "username", source = "username")
    @Mapping(target = "firstName", source = "firstName")
    @Mapping(target = "lastName", source = "lastName")
    @Mapping(target = "status", source = "status")
    @Mapping(target = "type", source = "type")
    @Mapping(target = "mfaEnabled", source = "mfaEnabled")
    @Mapping(target = "passwordExpired", source = "passwordExpired")
    @Mapping(target = "lastLoginAt", source = "lastLoginAt")
    @Mapping(target = "metadata", source = "metadata")
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "version", ignore = true)
    @Mapping(target = "identity", ignore = true)
    @Mapping(target = "account", ignore = true)
    UserEntity toEntity(User user);

    @Mapping(target = "id", source = "id", qualifiedByName = "uuidToUserId")
    @Mapping(target = "spaceId", source = "spaceId", qualifiedByName = "uuidToSpaceId")
    @Mapping(target = "accountId", source = "accountId", qualifiedByName = "uuidToAccountId")
    @Mapping(target = "username", source = "username")
    @Mapping(target = "firstName", source = "firstName")
    @Mapping(target = "lastName", source = "lastName")
    @Mapping(target = "status", source = "status")
    @Mapping(target = "type", source = "type")
    @Mapping(target = "mfaEnabled", source = "mfaEnabled")
    @Mapping(target = "passwordExpired", source = "passwordExpired")
    @Mapping(target = "lastLoginAt", source = "lastLoginAt")
    @Mapping(target = "metadata", source = "metadata")
    @Mapping(target = "createdAt", source = "createdAt")
    @Mapping(target = "updatedAt", source = "updatedAt")
    @Mapping(target = "version", source = "version")
    @Mapping(source = "account", target = "account", ignore = true)
    User toDomain(UserEntity entity);

    @Named("userIdToUuid")
    default UUID userIdToUuid(UserId id) {
        return (id == null) ? null : id.getValue();
    }

    @Named("uuidToUserId")
    default UserId uuidToUserId(UUID id) {
        return (id == null) ? null : UserId.of(id);
    }

    @Named("spaceIdToUuid")
    default UUID spaceIdToUuid(SpaceId id) {
        return (id == null) ? null : id.getValue();
    }

    @Named("uuidToSpaceId")
    default SpaceId uuidToSpaceId(UUID id) {
        return (id == null) ? null : SpaceId.of(id);
    }

    @Named("accountIdToUuid")
    default UUID accountIdToUuid(AccountId id) {
        return (id == null) ? null : id.getValue();
    }

    @Named("uuidToAccountId")
    default AccountId uuidToAccountId(UUID id) {
        return (id == null) ? null : AccountId.of(id);
    }
}