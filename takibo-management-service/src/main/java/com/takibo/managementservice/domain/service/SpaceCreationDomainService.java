package com.takibo.managementservice.domain.service;

import com.takibo.managementservice.domain.exception.OrganizationDisabledException;
import com.takibo.managementservice.domain.exception.SpaceQuotaExceededException;
import com.takibo.managementservice.domain.model.OrganizationContext;
import com.takibo.managementservice.domain.model.Space;
import com.takibo.managementservice.domain.vo.SpaceId;

import java.time.Instant;
import java.util.UUID;

public final class SpaceCreationDomainService {

    public Space createSpace(OrganizationContext organization,
                             UUID ownerAccountId,
                             String code,
                             String name,
                             String description,
                             SpaceId spaceId,
                             Instant now) {
        if (!organization.enabled()) {
            throw new OrganizationDisabledException(organization.orgId());
        }

        if (organization.quotaExceeded()) {
            throw new SpaceQuotaExceededException(
                    organization.orgId(),
                    10,
                    organization.currentSpaces()
            );
        }

        return Space.createNew(
                spaceId,
                organization.orgId(),
                ownerAccountId,
                code,
                name,
                description,
                now
        );
    }
}
