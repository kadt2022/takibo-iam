package com.takibo.managementservice.interfaces.rest.mapper;

import com.takibo.managementservice.application.command.OrganizationSignupCommand;
import com.takibo.managementservice.application.result.OrganizationSignupResult;
import com.takibo.managementservice.interfaces.rest.request.OrganizationSignupRequest;
import com.takibo.managementservice.interfaces.rest.response.OrganizationSignupResponse;
import org.springframework.stereotype.Component;

@Component
public class OrganizationSignupRestMapper {

    public OrganizationSignupCommand toCommand(OrganizationSignupRequest request) {
        return new OrganizationSignupCommand(
                new OrganizationSignupCommand.Organization(
                        request.organization().id(),
                        request.organization().code(),
                        request.organization().name()),
                new OrganizationSignupCommand.Space(
                        request.space().code(),
                        request.space().name(),
                        request.space().description()),
                new OrganizationSignupCommand.Account(
                        request.account().email(),
                        request.account().password()),
                new OrganizationSignupCommand.Profile(
                        request.profile().username(),
                        request.profile().firstName(),
                        request.profile().lastName())
        );
    }

    public OrganizationSignupResponse toResponse(OrganizationSignupResult result) {
        return new OrganizationSignupResponse(
                result.organizationId(),
                result.spaceId(),
                result.accountId(),
                result.userId()
        );
    }
}
