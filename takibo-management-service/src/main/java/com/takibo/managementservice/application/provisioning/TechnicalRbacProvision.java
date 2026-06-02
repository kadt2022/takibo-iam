package com.takibo.managementservice.application.provisioning;

import com.takibo.identitycore.application.rbac.governance.port.GroupAssignmentCase;
import com.takibo.identitycore.application.rbac.governance.port.RoleAssignmentCase;
import com.takibo.identitycore.domain.model.Identity;
import com.takibo.identitycore.domain.model.IdentityType;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class TechnicalRbacProvision {

    private final RoleAssignmentCase roleAssignmentCase;
    private final GroupAssignmentCase groupAssignmentCase;

    public TechnicalRbacProvision(RoleAssignmentCase roleAssignmentCase,
                                  GroupAssignmentCase groupAssignmentCase) {
        this.roleAssignmentCase = roleAssignmentCase;
        this.groupAssignmentCase = groupAssignmentCase;
    }

    public void provisionFounder(UUID orgId,
                                 UUID initialSpaceId,
                                 UUID founderAccountId,
                                 String systemActor) {

        Identity founder = new Identity(IdentityType.ACCOUNT, founderAccountId);

        roleAssignmentCase.assignTechnicalRole(orgId, initialSpaceId, founder, "R_ORG_OWNER", systemActor);
        roleAssignmentCase.assignTechnicalRole(orgId, initialSpaceId, founder, "R_SPACE_ADMIN", systemActor);

        groupAssignmentCase.assignTechnicalGroup(orgId, initialSpaceId, founder, "G_ORG_ADMINS", systemActor);
        groupAssignmentCase.assignTechnicalGroup(orgId, initialSpaceId, founder, "G_SPACE_ADMINS", systemActor);
    }

    public void provisionSpaceCreator(UUID orgId,
                                      UUID spaceId,
                                      UUID creatorAccountId,
                                      String systemActor) {

        Identity creator = new Identity(IdentityType.ACCOUNT, creatorAccountId);

        roleAssignmentCase.assignTechnicalRole(orgId, spaceId, creator, "R_SPACE_ADMIN", systemActor);
        groupAssignmentCase.assignTechnicalGroup(orgId, spaceId, creator, "G_SPACE_ADMINS", systemActor);
    }
}
