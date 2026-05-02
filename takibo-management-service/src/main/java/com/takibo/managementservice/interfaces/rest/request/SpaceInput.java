package com.takibo.managementservice.interfaces.rest.request;

import jakarta.validation.constraints.Size;

public record SpaceInput(
  @Size(min=2, max=80) String code,
  @Size(min=1, max=160) String name,
  @Size(max=255) String description
) {}