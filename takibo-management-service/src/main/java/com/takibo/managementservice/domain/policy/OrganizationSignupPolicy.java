package com.takibo.managementservice.domain.policy;

import com.takibo.managementservice.domain.model.OrganizationSignupDecision;

import java.util.Optional;
import java.util.UUID;
import java.util.function.Predicate;

public final class OrganizationSignupPolicy {

    public OrganizationSignupDecision decide(
            UUID existingOrganizationId,
            String code,
            String name
    ) {
        return Optional.ofNullable(existingOrganizationId)
                .<OrganizationSignupDecision>map(
                        OrganizationSignupDecision
                                .ExistingOrganizationForbidden::new
                )
                .orElseGet(() -> new OrganizationSignupDecision.CreateNew(
                        requireText(
                                code,
                                "organization.code is required when id is absent"
                        ),
                        requireText(
                                name,
                                "organization.name is required when id is absent"
                        )
                ));
    }

    private static String requireText(String value, String message) {
        return Optional.ofNullable(value)
                .filter(Predicate.not(String::isBlank))
                .orElseThrow(() -> new IllegalArgumentException(message));
    }
}
