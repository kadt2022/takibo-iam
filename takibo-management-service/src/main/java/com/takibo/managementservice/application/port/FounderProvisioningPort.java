package com.takibo.managementservice.application.port;

import java.util.UUID;

public interface FounderProvisioningPort {

    UUID provisionFounder(UUID organizationId,
                          UUID spaceId,
                          UUID accountId,
                          String username,
                          String firstName,
                          String lastName);
}
