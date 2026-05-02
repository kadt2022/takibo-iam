package com.takibo.identitycore.application.identity.mapper;

import com.takibo.identitycore.domain.model.EmailAddress;
import com.takibo.identitycore.domain.model.User;
import com.takibo.identitycore.interfaces.rest.response.UserResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface UserMapper {

    @Mapping(target = "id",            source = "user.id.value")
    @Mapping(target = "spaceId",       source = "user.spaceId.value")
    @Mapping(target = "username",      source = "user.username")
    @Mapping(target = "email",         source = "email.value")
    @Mapping(target = "firstName",     source = "user.firstName")
    @Mapping(target = "lastName",      source = "user.lastName")
    @Mapping(target = "status",        source = "user.status")
    @Mapping(target = "type",          source = "user.type")
    @Mapping(target = "mfaEnabled",    source = "user.mfaEnabled")
    @Mapping(target = "passwordExpired", source = "user.passwordExpired")
    @Mapping(target = "lastLoginAt",   source = "user.lastLoginAt")
    @Mapping(target = "createdAt",     source = "user.createdAt")
    @Mapping(target = "updatedAt",     source = "user.updatedAt")
    @Mapping(target = "version",       source = "user.version")
    UserResponse toUserResponse(User user, EmailAddress email);
}
