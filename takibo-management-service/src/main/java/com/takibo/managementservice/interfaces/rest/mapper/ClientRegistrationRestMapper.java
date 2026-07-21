package com.takibo.managementservice.interfaces.rest.mapper;

import com.takibo.managementservice.application.command.RegisterClientCommand;
import com.takibo.managementservice.domain.model.OAuthClient;
import com.takibo.managementservice.domain.vo.OAuthClientId;
import com.takibo.managementservice.domain.vo.SpaceId;
import com.takibo.managementservice.interfaces.rest.request.ClientRegistrationRequest;
import com.takibo.managementservice.interfaces.rest.response.ClientRegistrationResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

import java.util.UUID;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface ClientRegistrationRestMapper {

    RegisterClientCommand toCommand(ClientRegistrationRequest request);

    @Mapping(target = "id", source = "id")
    @Mapping(target = "orgId", source = "orgId")
    @Mapping(target = "spaceId", source = "spaceId")
    ClientRegistrationResponse toResponse(OAuthClient client);

    default UUID map(OAuthClientId id) {
        return id == null ? null : id.getValue();
    }

    default UUID map(SpaceId id) {
        return id == null ? null : id.value();
    }
}
