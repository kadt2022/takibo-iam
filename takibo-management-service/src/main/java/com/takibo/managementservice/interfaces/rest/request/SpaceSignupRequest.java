package com.takibo.managementservice.interfaces.rest.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

public record SpaceSignupRequest(
        @NotNull @Valid SpacePayload space,
        @NotNull @Valid AccountPayload account,
        @NotNull @Valid ProfilePayload profile
) {}
