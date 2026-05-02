package com.takibo.managementservice.interfaces.rest.response;

import java.util.UUID;

public record OrganizationSignupResponse(
  UUID organizationId,
  UUID spaceId,
  UUID accountId,
  UUID userId
) {}