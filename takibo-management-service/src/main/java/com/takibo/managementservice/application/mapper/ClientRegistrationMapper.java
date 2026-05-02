package com.takibo.managementservice.application.mapper;

import com.takibo.managementservice.application.command.RegisterClientCommand;
import com.takibo.managementservice.domain.model.OAuthClient;
import com.takibo.managementservice.domain.vo.SpaceId;
import com.takibo.managementservice.domain.vo.OAuthClientId;
import com.takibo.managementservice.interfaces.rest.request.ClientRegistrationRequest;
import com.takibo.managementservice.interfaces.rest.response.ClientRegistrationResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

import java.util.UUID;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface ClientRegistrationMapper {

    RegisterClientCommand toCommand(ClientRegistrationRequest req);

    @Mapping(target = "id", source = "id")
    @Mapping(target = "orgId", source = "orgId")
    @Mapping(target = "spaceId", source = "spaceId")
    @Mapping(target = "clientId", source = "clientId")
    @Mapping(target = "clientName", source = "clientName")
    @Mapping(target = "clientType", source = "clientType")
    @Mapping(target = "requireClientSecret", source = "requireClientSecret")
    @Mapping(target = "requirePkce", source = "requirePkce")
    @Mapping(target = "clientSecretExpiresAt", source = "clientSecretExpiresAt")
    @Mapping(target = "scopes", source = "scopes")
    @Mapping(target = "grantTypes", source = "grantTypes")
    @Mapping(target = "redirectUris", source = "redirectUris")
    @Mapping(target = "postLogoutRedirectUris", source = "postLogoutRedirectUris")
    @Mapping(target = "corsOrigins", source = "corsOrigins")
    ClientRegistrationResponse toResponse(OAuthClient client);

    default UUID map(OAuthClientId id) { return id == null ? null : id.getValue(); }
    default UUID map(SpaceId id)       { return id == null ? null : id.value(); }
}
