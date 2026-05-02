package com.takibo.managementservice.interfaces.rest.request;

import jakarta.validation.constraints.Size;

public record ProfileInput(
  @Size(min=2, max=150) String username,
  @Size(min=1, max=160) String firstName,
  @Size(min=1, max=160) String lastName
) {}