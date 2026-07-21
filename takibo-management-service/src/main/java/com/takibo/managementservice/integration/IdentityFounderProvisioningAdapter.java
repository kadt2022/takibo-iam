package com.takibo.managementservice.integration;

import com.takibo.identitycore.application.identity.command.ProvisionFounderUserCommand;
import com.takibo.identitycore.application.identity.port.FounderUserProvisioningCase;
import com.takibo.managementservice.application.port.FounderProvisioningPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@RequiredArgsConstructor
public class IdentityFounderProvisioningAdapter implements FounderProvisioningPort {

    private final FounderUserProvisioningCase founders;

    @Override
    public UUID provisionFounder(UUID organizationId,
                                 UUID spaceId,
                                 UUID accountId,
                                 String username,
                                 String firstName,
                                 String lastName) {
        ProvisionFounderUserCommand command = new ProvisionFounderUserCommand(
                organizationId,
                spaceId,
                accountId,
                username,
                firstName,
                lastName
        );
        return founders.provisionFounder(command).id();
    }
}
