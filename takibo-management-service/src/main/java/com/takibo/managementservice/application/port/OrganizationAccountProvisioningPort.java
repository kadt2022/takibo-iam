package com.takibo.managementservice.application.port;

import java.util.UUID;

public interface OrganizationAccountProvisioningPort {

    UUID createAccount(UUID organizationId, String email, String password);
}
