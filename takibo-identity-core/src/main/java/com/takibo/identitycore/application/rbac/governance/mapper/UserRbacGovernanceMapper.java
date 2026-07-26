package com.takibo.identitycore.application.rbac.governance.mapper;

import com.takibo.identitycore.application.rbac.catalog.model.CatalogNature;
import com.takibo.identitycore.application.rbac.catalog.model.CatalogOrigin;
import com.takibo.identitycore.domain.catalogrbac.AuthorityPlan;
import com.takibo.identitycore.domain.catalogrbac.TechnicalGroup;
import com.takibo.identitycore.domain.catalogrbac.TechnicalRole;
import com.takibo.identitycore.domain.rbac.model.GroupAssignment;
import com.takibo.identitycore.domain.rbac.model.GroupSource;
import com.takibo.identitycore.domain.rbac.model.RoleAssignment;
import com.takibo.identitycore.domain.rbac.model.RoleSource;
import com.takibo.identitycore.interfaces.rest.response.UserGroupMembershipResponse;
import com.takibo.identitycore.interfaces.rest.response.UserGroupMembershipsResponse;
import com.takibo.identitycore.interfaces.rest.response.UserRoleAssignmentResponse;
import com.takibo.identitycore.interfaces.rest.response.UserRoleAssignmentsResponse;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * Présentation des assignations directes. Le discriminant origin/nature/scope est
 * dérivé de la source persistée : TECHNICAL -> catalogue enum, GOVERNANCE -> ligne
 * tenant du space. {@code source=DIRECT} : cette surface ne raconte pas l'héritage.
 */
@Component
public class UserRbacGovernanceMapper {

    private static final String DIRECT = "DIRECT";

    public UserRoleAssignmentsResponse toRolesResponse(UUID userId, List<RoleAssignment> assignments) {
        List<UserRoleAssignmentResponse> roles = assignments.stream()
                .map(this::toRoleItem)
                .distinct()
                .sorted(Comparator.comparing(UserRoleAssignmentResponse::code))
                .toList();
        return new UserRoleAssignmentsResponse(userId, roles);
    }

    public UserGroupMembershipsResponse toGroupsResponse(UUID userId, List<GroupAssignment> memberships) {
        List<UserGroupMembershipResponse> groups = memberships.stream()
                .map(this::toGroupItem)
                .distinct()
                .sorted(Comparator.comparing(UserGroupMembershipResponse::code))
                .toList();
        return new UserGroupMembershipsResponse(userId, groups);
    }

    private UserRoleAssignmentResponse toRoleItem(RoleAssignment assignment) {
        if (assignment.roleSource() == RoleSource.TECHNICAL) {
            AuthorityPlan plan = TechnicalRole.fromCode(assignment.roleCode())
                    .map(TechnicalRole::plan)
                    .orElse(AuthorityPlan.SPACE);
            return new UserRoleAssignmentResponse(
                    assignment.roleCode(), CatalogOrigin.TECHNICAL, CatalogNature.TECHNICAL, plan, DIRECT);
        }
        return new UserRoleAssignmentResponse(
                assignment.roleCode(), CatalogOrigin.DATABASE, CatalogNature.GOVERNANCE,
                AuthorityPlan.SPACE, DIRECT);
    }

    private UserGroupMembershipResponse toGroupItem(GroupAssignment membership) {
        if (membership.groupSource() == GroupSource.TECHNICAL) {
            AuthorityPlan plan = TechnicalGroup.fromCode(membership.groupCode())
                    .map(TechnicalGroup::plan)
                    .orElse(AuthorityPlan.SPACE);
            return new UserGroupMembershipResponse(
                    membership.groupCode(), CatalogOrigin.TECHNICAL, CatalogNature.TECHNICAL, plan, DIRECT);
        }
        return new UserGroupMembershipResponse(
                membership.groupCode(), CatalogOrigin.DATABASE, CatalogNature.GOVERNANCE,
                AuthorityPlan.SPACE, DIRECT);
    }
}
