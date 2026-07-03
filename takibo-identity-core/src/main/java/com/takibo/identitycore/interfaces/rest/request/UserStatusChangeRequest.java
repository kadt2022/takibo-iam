package com.takibo.identitycore.interfaces.rest.request;

import jakarta.validation.constraints.Size;

/** Justification administrative optionnelle — audit/logs uniquement. */
public record UserStatusChangeRequest(
        @Size(max = 500) String reason
) {
}
