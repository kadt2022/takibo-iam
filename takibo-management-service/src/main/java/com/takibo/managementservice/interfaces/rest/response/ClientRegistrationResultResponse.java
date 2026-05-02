package com.takibo.managementservice.interfaces.rest.response;

public record ClientRegistrationResultResponse(
        ClientRegistrationResponse client,
        String oneTimePlainSecret
) {}
