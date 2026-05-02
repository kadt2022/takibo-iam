package com.takibo.managementservice.application.service;

import com.takibo.managementservice.application.port.OrganizationReadPort;
import com.takibo.managementservice.domain.exception.OrganizationDisabledException;
import com.takibo.managementservice.domain.exception.SpaceQuotaExceededException;

import com.takibo.managementservice.domain.model.OrganizationContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class OrganizationDomainService {

    private final OrganizationReadPort organizationReadPort;

    public OrganizationContext assertOrganizationAllowsSpaceCreation(UUID orgId) {
        OrganizationContext ctx = organizationReadPort.getOrganizationContext(orgId);

        if (!ctx.enabled()) {
            throw new OrganizationDisabledException(orgId);
        }
        if (ctx.quotaExceeded()) {
            throw new SpaceQuotaExceededException(orgId, 10, ctx.currentSpaces());
        }
        return ctx;
    }
}
