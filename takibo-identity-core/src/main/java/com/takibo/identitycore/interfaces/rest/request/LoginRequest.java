package com.takibo.identitycore.interfaces.rest.request;

import jakarta.validation.constraints.NotBlank;

public record LoginRequest(
        @NotBlank(message = "email is required") String email,
        @NotBlank(message = "password is required") String password,
        @NotBlank(message = "orgCode is required") String orgCode,
        @NotBlank(message = "spaceCode is required") String spaceCode
) {
}
