package com.takibo.identitycore.application.rbac.effective.service;

import com.takibo.identitycore.application.rbac.effective.model.EffectiveRbac;
import com.takibo.identitycore.application.rbac.effective.port.in.EffectiveRbacQueryCase;
import com.takibo.identitycore.domain.catalogrbac.TechnicalGroup;
import com.takibo.identitycore.domain.catalogrbac.TechnicalRole;
import com.takibo.identitycore.domain.catalogrbac.TechnicalScope;
import com.takibo.identitycore.domain.rbac.model.GroupAssignment;
import com.takibo.identitycore.domain.rbac.model.GroupSource;
import com.takibo.identitycore.domain.rbac.model.RoleAssignment;
import com.takibo.identitycore.domain.rbac.repository.GovernanceGroupAssignmentRepository;
import com.takibo.identitycore.domain.rbac.repository.GovernanceRoleAssignmentRepository;
import com.takibo.identitycore.domain.repository.GroupRoleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;

/**
 * Le pouvoir effectif d'un account dans un space, calculé au moment de
 * l'authentification :
 * <pre>
 *   rôles directs (TECHNICAL + GOVERNANCE)
 * + groupes directs (TECHNICAL + GOVERNANCE)
 * + rôles hérités des groupes techniques (enum TechnicalGroup)
 * + rôles hérités des groupes DB (liens group_roles, rôles GOVERNANCE)
 * + permissions des rôles techniques effectifs (enum TechnicalRole)
 * </pre>
 * Les assignations BUSINESS sont ignorées (récit dédié). Les codes techniques
 * de scope SYSTEM/USER ne sont jamais racontés aux tenants : même une ligne
 * seedée en base ne les fait pas entrer dans un token tenant.
 * <p>
 * Frontière stricte : tout est lu pour (org, space, account) — les assignments
 * org-level du même org (space NULL) sont inclus, rien d'un autre org/space.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class EffectiveRbacQueryService implements EffectiveRbacQueryCase {

    private static final Set<TechnicalScope> TENANT_VISIBLE_SCOPES =
            EnumSet.of(TechnicalScope.ORGANIZATION, TechnicalScope.SPACE);

    private final GovernanceRoleAssignmentRepository roleAssignments;
    private final GovernanceGroupAssignmentRepository groupMemberships;
    private final GroupRoleRepository groupRoles;

    @Override
    public EffectiveRbac effectiveFor(UUID orgId, UUID spaceId, UUID accountId) {
        Objects.requireNonNull(orgId, "orgId");
        Objects.requireNonNull(spaceId, "spaceId");
        Objects.requireNonNull(accountId, "accountId");

        // TreeSet : déduplication + tri — le token doit être déterministe.
        Set<String> roles = new TreeSet<>();
        Set<String> groups = new TreeSet<>();

        // 1) Rôles directs (BUSINESS déjà exclu par le repository).
        roleAssignments.findDirectAssignments(orgId, spaceId, accountId).stream()
                .map(RoleAssignment::roleCode)
                .filter(this::isTenantVisibleRoleCode)
                .forEach(roles::add);

        // 2) Groupes directs (BUSINESS déjà exclu par le repository).
        List<GroupAssignment> memberships =
                groupMemberships.findDirectMemberships(orgId, spaceId, accountId);
        memberships.stream()
                .map(GroupAssignment::groupCode)
                .filter(this::isTenantVisibleGroupCode)
                .forEach(groups::add);

        // 3) Héritage des groupes techniques : les liens vivent dans le code.
        memberships.stream()
                .filter(m -> m.groupSource() == GroupSource.TECHNICAL)
                .map(GroupAssignment::groupCode)
                .map(TechnicalGroup::fromCode)
                .flatMap(Optional::stream)
                .filter(group -> TENANT_VISIBLE_SCOPES.contains(group.scope()))
                .flatMap(group -> group.roles().stream())
                .filter(role -> TENANT_VISIBLE_SCOPES.contains(role.scope()))
                .map(TechnicalRole::code)
                .forEach(roles::add);

        // 4) Héritage des groupes DB : les liens vivent dans group_roles,
        //    seuls les rôles GOVERNANCE sont transmis.
        List<String> governanceGroupCodes = memberships.stream()
                .filter(m -> m.groupSource() == GroupSource.GOVERNANCE)
                .map(GroupAssignment::groupCode)
                .filter(Objects::nonNull)
                .toList();
        if (!governanceGroupCodes.isEmpty()) {
            roles.addAll(groupRoles.findGovernanceRoleCodesByGroups(orgId, spaceId, governanceGroupCodes));
        }

        // 5) Permissions des rôles techniques effectifs (directs + hérités).
        Set<String> permissions = new TreeSet<>();
        roles.stream()
                .map(TechnicalRole::fromCode)
                .flatMap(Optional::stream)
                .flatMap(role -> role.permissions().stream())
                .filter(permission -> TENANT_VISIBLE_SCOPES.contains(permission.scope()))
                .map(TechnicalGroup.TechnicalPermission::code)
                .forEach(permissions::add);

        return new EffectiveRbac(List.copyOf(roles), List.copyOf(groups), List.copyOf(permissions));
    }

    @Override
    public EffectiveRbac effectiveOrgFor(UUID orgId, UUID accountId) {
        Objects.requireNonNull(orgId, "orgId");
        Objects.requireNonNull(accountId, "accountId");

        Set<String> roles = new TreeSet<>();
        Set<String> groups = new TreeSet<>();

        // 1) Rôles directs org-level — seuls les codes techniques de scope
        //    ORGANIZATION entrent : une ligne org-level anormale (code SPACE,
        //    code inconnu) est ignorée en lecture, pas seulement refusée en écriture.
        roleAssignments.findOrgLevelAssignments(orgId, accountId).stream()
                .map(RoleAssignment::roleCode)
                .filter(this::isOrganizationRoleCode)
                .forEach(roles::add);

        // 2) Groupes directs org-level (même filtre strict de scope).
        List<GroupAssignment> memberships =
                groupMemberships.findOrgLevelMemberships(orgId, accountId);
        memberships.stream()
                .map(GroupAssignment::groupCode)
                .filter(this::isOrganizationGroupCode)
                .forEach(groups::add);

        // 3) Héritage des groupes techniques ORGANIZATION : uniquement leurs
        //    rôles de scope ORGANIZATION (G_ORG_ADMINS transmet R_ORG_OWNER/ADMIN).
        memberships.stream()
                .filter(m -> m.groupSource() == GroupSource.TECHNICAL)
                .map(GroupAssignment::groupCode)
                .map(TechnicalGroup::fromCode)
                .flatMap(Optional::stream)
                .filter(group -> group.scope() == TechnicalScope.ORGANIZATION)
                .flatMap(group -> group.roles().stream())
                .filter(role -> role.scope() == TechnicalScope.ORGANIZATION)
                .map(TechnicalRole::code)
                .forEach(roles::add);

        // Pas d'héritage group_roles : les groupes GOVERNANCE sont des lignes
        // situées dans un space (doctrine PR #26) — rien d'org-level à hériter.

        // 4) Permissions des rôles effectifs, scope ORGANIZATION exclusivement.
        Set<String> permissions = new TreeSet<>();
        roles.stream()
                .map(TechnicalRole::fromCode)
                .flatMap(Optional::stream)
                .flatMap(role -> role.permissions().stream())
                .filter(permission -> permission.scope() == TechnicalScope.ORGANIZATION)
                .map(TechnicalGroup.TechnicalPermission::code)
                .forEach(permissions::add);

        return new EffectiveRbac(List.copyOf(roles), List.copyOf(groups), List.copyOf(permissions));
    }

    /** Seul un code technique de scope ORGANIZATION entre dans un token ORG. */
    private boolean isOrganizationRoleCode(String code) {
        if (code == null) {
            return false;
        }
        return TechnicalRole.fromCode(code)
                .map(role -> role.scope() == TechnicalScope.ORGANIZATION)
                .orElse(false);
    }

    private boolean isOrganizationGroupCode(String code) {
        if (code == null) {
            return false;
        }
        return TechnicalGroup.fromCode(code)
                .map(group -> group.scope() == TechnicalScope.ORGANIZATION)
                .orElse(false);
    }

    /** Un code technique de scope non visible tenant n'entre jamais dans un token tenant. */
    private boolean isTenantVisibleRoleCode(String code) {
        if (code == null) {
            return false;
        }
        return TechnicalRole.fromCode(code)
                .map(role -> TENANT_VISIBLE_SCOPES.contains(role.scope()))
                .orElse(true);
    }

    private boolean isTenantVisibleGroupCode(String code) {
        if (code == null) {
            return false;
        }
        return TechnicalGroup.fromCode(code)
                .map(group -> TENANT_VISIBLE_SCOPES.contains(group.scope()))
                .orElse(true);
    }
}
