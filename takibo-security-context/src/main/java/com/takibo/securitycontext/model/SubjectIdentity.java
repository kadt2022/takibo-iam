package com.takibo.securitycontext.model;

import java.util.Set;

import static com.takibo.securitycontext.validation.TakiboAsserts.*;

public record SubjectIdentity(
        String subjectId,
        SubjectNature nature,
        Set<String> declaredRoles,
        AuthenticationMethod authenticationMethod
) {
    public SubjectIdentity {
        notBlank(subjectId, "subjectId is required");
        notNull(nature, "nature is required");
        notNull(authenticationMethod, "authenticationMethod is required");

        declaredRoles = declaredRoles == null ? Set.of() : Set.copyOf(declaredRoles);

        for (String role : declaredRoles) {
            notBlank(role, "declaredRoles must not contain null/blank values");
            maxLength(role, 128, "declaredRoles contains an oversized role");
        }
    }
}
