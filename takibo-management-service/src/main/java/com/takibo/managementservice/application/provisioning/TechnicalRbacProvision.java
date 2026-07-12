package com.takibo.managementservice.application.provisioning;

import com.takibo.identitycore.application.rbac.governance.port.in.GroupAssignmentCase;
import com.takibo.identitycore.application.rbac.governance.port.in.RoleAssignmentCase;
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

        // IAM 31 : l'autorité d'organisation du fondateur est org-level (space NULL) —
        // elle ne doit jamais dépendre de l'existence du space initial.
        roleAssignmentCase.assignTechnicalRole(orgId, null, founder, "R_ORG_OWNER", systemActor);
        groupAssignmentCase.assignTechnicalGroup(orgId, null, founder, "G_ORG_ADMINS", systemActor);

        // Son pouvoir d'administration du space initial, lui, est bien situé.
        roleAssignmentCase.assignTechnicalRole(orgId, initialSpaceId, founder, "R_SPACE_ADMIN", systemActor);
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
