package com.takibo.managementservice.application.result;

import java.util.UUID;

public record OrganizationSignupResult(
        UUID organizationId,
        UUID spaceId,
        UUID accountId,
        UUID userId
) {}
