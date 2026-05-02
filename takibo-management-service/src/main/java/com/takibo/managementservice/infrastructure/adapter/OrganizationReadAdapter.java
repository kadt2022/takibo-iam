package com.takibo.managementservice.infrastructure.adapter;

import com.takibo.managementservice.application.port.OrganizationReadPort;
import com.takibo.managementservice.domain.model.OrganizationContext;
import com.takibo.managementservice.domain.model.OrganizationStatus;
import com.takibo.managementservice.infrastructure.jpa.repository.JpaSpaceRepository;
import com.takibo.managementservice.infrastructure.jpa.repository.OrganizationJpaRepository;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class OrganizationReadAdapter implements OrganizationReadPort {

    private final OrganizationJpaRepository organizationRepository;
    private final JpaSpaceRepository spaceRepository;

    public OrganizationReadAdapter(OrganizationJpaRepository organizationRepository,
                                   JpaSpaceRepository spaceRepository) {
        this.organizationRepository = organizationRepository;
        this.spaceRepository = spaceRepository;
    }

    @Override
    public boolean existsById(UUID orgId) {
        return organizationRepository.existsById(orgId);
    }

    @Override
    public OrganizationContext getOrganizationContext(UUID orgId) {
        var org = organizationRepository.findById(orgId)
                .orElseThrow(() -> new IllegalArgumentException("Organization not found: " + orgId));

        int currentSpaces = spaceRepository.countByOrgId(orgId);

        boolean enabled = org.getStatus() == OrganizationStatus.ACTIVE;

        return new OrganizationContext(
                orgId,
                enabled,
            //    org.getMaxSpaces(),
                currentSpaces
        );
    }
}
