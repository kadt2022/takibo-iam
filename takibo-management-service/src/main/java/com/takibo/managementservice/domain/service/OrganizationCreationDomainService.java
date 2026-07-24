package com.takibo.managementservice.domain.service;

import com.takibo.managementservice.domain.model.OrganizationCreationPlan;
import com.takibo.managementservice.domain.model.OrganizationStatus;
import com.takibo.managementservice.domain.normalization.OrganizationCodeNormalizer;

public final class OrganizationCreationDomainService {

    private final OrganizationCodeNormalizer codeNormalizer;

    public OrganizationCreationDomainService() {
        this(new OrganizationCodeNormalizer());
    }

    public OrganizationCreationDomainService(
            OrganizationCodeNormalizer codeNormalizer
    ) {
        this.codeNormalizer = codeNormalizer;
    }

    public OrganizationCreationPlan prepareCreation(
            String code,
            String name
    ) {
        return new OrganizationCreationPlan(
                codeNormalizer.normalize(code),
                name,
                OrganizationStatus.ACTIVE
        );
    }
}
