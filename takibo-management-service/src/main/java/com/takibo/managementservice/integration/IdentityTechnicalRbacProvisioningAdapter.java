package com.takibo.managementservice.integration;

import com.takibo.identitycore.application.rbac.governance.port.in.GroupAssignmentCase;
import com.takibo.identitycore.application.rbac.governance.port.in.RoleAssignmentCase;
import com.takibo.identitycore.domain.model.Identity;
import com.takibo.identitycore.domain.model.IdentityType;
import com.takibo.managementservice.application.port.TechnicalRbacProvisioningPort;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Adaptateur d'intégration TMS -> TIS-CORE : traduit l'intention de provisioning
 * RBAC de la couche application en assignations techniques de rôles et de groupes.
 * C'est le seul point où le domaine identité (Identity) et les cas d'usage RBAC
 * de TIS-CORE sont connus — la couche application reste derrière le port.
 */
@Component
public class IdentityTechnicalRbacProvisioningAdapter implements TechnicalRbacProvisioningPort {

    private final RoleAssignmentCase roleAssignmentCase;
    private final GroupAssignmentCase groupAssignmentCase;

    public IdentityTechnicalRbacProvisioningAdapter(RoleAssignmentCase roleAssignmentCase,
                                                    GroupAssignmentCase groupAssignmentCase) {
        this.roleAssignmentCase = roleAssignmentCase;
        this.groupAssignmentCase = groupAssignmentCase;
    }

    @Override
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

    @Override
    public void provisionSpaceCreator(UUID orgId,
                                      UUID spaceId,
                                      UUID creatorAccountId,
                                      String systemActor) {

        Identity creator = new Identity(IdentityType.ACCOUNT, creatorAccountId);

        roleAssignmentCase.assignTechnicalRole(orgId, spaceId, creator, "R_SPACE_ADMIN", systemActor);
        groupAssignmentCase.assignTechnicalGroup(orgId, spaceId, creator, "G_SPACE_ADMINS", systemActor);
    }
}
