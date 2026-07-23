package com.takibo.managementservice.domain.service;

import com.takibo.managementservice.domain.model.OrganizationCreationPlan;
import com.takibo.managementservice.domain.model.OrganizationStatus;

public final class OrganizationCreationDomainService {

    public OrganizationCreationPlan prepareCreation(String code, String name) {
        return new OrganizationCreationPlan(
                TakiboCodeNormalizer.normalizeOrg(code),
                name,
                OrganizationStatus.ACTIVE
        );
    }
}
