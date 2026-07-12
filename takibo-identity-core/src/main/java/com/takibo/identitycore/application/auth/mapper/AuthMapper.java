package com.takibo.identitycore.application.auth.mapper;

import com.takibo.identitycore.application.auth.command.LoginCommand;
import com.takibo.identitycore.application.auth.model.HumanTokenRequest;
import com.takibo.identitycore.application.auth.model.LoginToken;
import com.takibo.identitycore.interfaces.rest.request.LoginRequest;
import com.takibo.identitycore.interfaces.rest.response.LoginResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface AuthMapper {

    LoginCommand toCommand(LoginRequest request);

    @Mapping(target = "accessToken",    source = "token.accessToken")
    @Mapping(target = "tokenType",      source = "token.tokenType")
    @Mapping(target = "expiresIn",      source = "token.expiresIn")
    @Mapping(target = "scopeLevel",
            expression = "java(identity.isOrganizationScoped() ? \"ORGANIZATION\" : \"SPACE\")")
    @Mapping(target = "organizationId", source = "identity.orgId")
    @Mapping(target = "spaceId",        source = "identity.spaceId")
    @Mapping(target = "accountId",      source = "identity.accountId")
    @Mapping(target = "userId",         source = "identity.userId")
    LoginResponse toLoginResponse(LoginToken token, HumanTokenRequest identity);
}
