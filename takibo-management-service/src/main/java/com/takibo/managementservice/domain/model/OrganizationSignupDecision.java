package com.takibo.managementservice.domain.model;

import java.util.UUID;

public sealed interface OrganizationSignupDecision
        permits OrganizationSignupDecision.CreateNew,
                OrganizationSignupDecision.ExistingOrganizationForbidden {

    record CreateNew(String code, String name)
            implements OrganizationSignupDecision {
    }

    record ExistingOrganizationForbidden(UUID organizationId)
            implements OrganizationSignupDecision {
    }
}
