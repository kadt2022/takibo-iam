package com.takibo.managementservice.domain.service;

import com.takibo.managementservice.domain.factory.SpaceFactory;
import com.takibo.managementservice.domain.model.OrganizationContext;
import com.takibo.managementservice.domain.model.Space;
import com.takibo.managementservice.domain.model.SpaceCreationRequest;
import com.takibo.managementservice.domain.policy.SpaceCreationEligibilityPolicy;
import com.takibo.managementservice.domain.vo.SpaceId;

import java.time.Instant;
import java.util.UUID;

public final class SpaceCreationDomainService {

    private final SpaceCreationEligibilityPolicy eligibilityPolicy;
    private final SpaceFactory spaceFactory;

    public SpaceCreationDomainService() {
        this(new SpaceCreationEligibilityPolicy(), new SpaceFactory());
    }

    public SpaceCreationDomainService(
            SpaceCreationEligibilityPolicy eligibilityPolicy,
            SpaceFactory spaceFactory
    ) {
        this.eligibilityPolicy = eligibilityPolicy;
        this.spaceFactory = spaceFactory;
    }

    public void assertEligibleForCreation(OrganizationContext organization) {
        eligibilityPolicy.validateEligibility(organization);
    }

    public Space createSpace(SpaceCreationRequest request) {
        eligibilityPolicy.validateEligibility(request.organization());
        return spaceFactory.createSpace(request);
    }

    public Space createSpace(
            OrganizationContext organization,
            UUID ownerAccountId,
            String code,
            String name,
            String description,
            SpaceId spaceId,
            Instant now
    ) {
        return createSpace(new SpaceCreationRequest(
                organization,
                ownerAccountId,
                code,
                name,
                description,
                spaceId,
                now
        ));
    }
}
