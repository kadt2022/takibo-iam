package com.takibo.managementservice.application.port;



import com.takibo.managementservice.domain.model.OrganizationContext;

import java.util.UUID;

public interface OrganizationReadPort {
    OrganizationContext getOrganizationContext(UUID orgId);
    OrganizationContext getOrganizationContextForSpaceCreation(UUID orgId);
    boolean existsById(UUID orgId);
}
