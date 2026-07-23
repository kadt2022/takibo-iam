package com.takibo.managementservice.integration;

import com.takibo.identitycore.application.identity.port.AccountApplicationCase;
import com.takibo.managementservice.application.port.OrganizationAccountProvisioningPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@RequiredArgsConstructor
public class IdentityAccountProvisioningAdapter implements OrganizationAccountProvisioningPort {

    private final AccountApplicationCase accounts;

    @Override
    public UUID createAccount(UUID organizationId, String email, String password) {
        return accounts.createAccountInOrg(organizationId, email, password).id();
    }
}
