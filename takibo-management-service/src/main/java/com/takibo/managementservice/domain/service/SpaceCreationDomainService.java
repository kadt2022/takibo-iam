package com.takibo.managementservice.domain.service;

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
