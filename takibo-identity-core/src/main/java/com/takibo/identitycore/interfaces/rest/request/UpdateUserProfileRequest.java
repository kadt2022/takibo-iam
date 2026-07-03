package com.takibo.identitycore.interfaces.rest.request;

import jakarta.validation.constraints.Size;

import java.util.Map;

/** PATCH partiel : un champ absent/null = inchangé. */
public record UpdateUserProfileRequest(
        @Size(min = 1, max = 150) String username,
        @Size(max = 160) String firstName,
        @Size(max = 160) String lastName,
        Map<String, Object> metadata
) {
}
