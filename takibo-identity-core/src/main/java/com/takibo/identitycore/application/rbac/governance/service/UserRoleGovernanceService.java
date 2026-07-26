package com.takibo.identitycore.application.rbac.governance.service;

import com.takibo.identitycore.application.rbac.governance.command.AssignUserRoleCommand;
import com.takibo.identitycore.application.rbac.governance.command.RemoveUserRoleCommand;
import com.takibo.identitycore.application.rbac.governance.mapper.UserRbacGovernanceMapper;
import com.takibo.identitycore.application.rbac.governance.port.in.UserRoleGovernanceCase;
import com.takibo.identitycore.domain.catalogrbac.TenantRoleCodePolicy;
import com.takibo.identitycore.domain.catalogrbac.TechnicalRole;
import com.takibo.identitycore.domain.catalogrbac.AuthorityPlan;
import com.takibo.identitycore.domain.exception.DuplicateAssignmentException;
import com.takibo.identitycore.domain.exception.LastAdminRemovalException;
import com.takibo.identitycore.domain.exception.RoleNotFoundException;
import com.takibo.identitycore.domain.exception.RoleScopeEscalationException;
import com.takibo.identitycore.domain.exception.RoleTypeNotAllowedException;
import com.takibo.identitycore.domain.exception.SelfDemotionException;
import com.takibo.identitycore.domain.exception.UserNotFoundException;
import com.takibo.identitycore.domain.model.Identity;
import com.takibo.identitycore.domain.model.IdentityType;
import com.takibo.identitycore.domain.model.RoleNature;
import com.takibo.identitycore.domain.model.User;
import com.takibo.identitycore.domain.rbac.model.RoleAssignment;
import com.takibo.identitycore.domain.rbac.model.RoleSource;
import com.takibo.identitycore.domain.rbac.repository.GovernanceRoleAssignmentRepository;
import com.takibo.identitycore.domain.repository.RoleRepository;
import com.takibo.identitycore.domain.repository.UserRepository;
import com.takibo.identitycore.domain.vo.SpaceId;
import com.takibo.identitycore.domain.vo.UserId;
import com.takibo.identitycore.integration.security.SpaceBoundaryGuard;
import com.takibo.identitycore.integration.security.port.CurrentAccountContextCase;
import com.takibo.identitycore.integration.space.SpaceContextVerifier;
import com.takibo.identitycore.integration.space.port.ResolvedSpaceKey;
import com.takibo.identitycore.interfaces.rest.response.UserRoleAssignmentsResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Délégation des rôles directs d'un user situé. Déléguer n'est jamais implicite :
 * c'est nommer (code du catalogue visible), situer (frontière du token), tracer
 * (audit) et pouvoir retirer (retrait idempotent).
 * <p>
 * Gardes, dans l'ordre : frontière du space, acteur humain situé, user du space
 * (404 anti-énumération), catalogue visible (SYSTEM/USER n'existent pas -> 404,
 * BUSINESS interdit -> 403), non-escalade verticale (un pouvoir ORGANIZATION ne se
 * délègue ni ne se retire sans autorité ORGANIZATION), self-demotion et dernier
 * chemin admin du space (409).
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class UserRoleGovernanceService implements UserRoleGovernanceCase {

    private static final Set<AuthorityPlan> ASSIGNABLE_PLANS =
            EnumSet.of(AuthorityPlan.ORGANIZATION, AuthorityPlan.SPACE);

    /** Autorité requise pour déléguer/retirer un pouvoir de scope ORGANIZATION. */
    private static final Set<String> ORG_AUTHORITY_ROLE_CODES = Set.of("R_ORG_OWNER", "R_ORG_ADMIN");

    /** Rôles d'administration : jamais auto-retirés sur cette surface. */
    private static final Set<String> ADMIN_ROLE_CODES = Set.of("R_ORG_OWNER", "R_ORG_ADMIN", "R_SPACE_ADMIN");

    private static final String SPACE_ADMIN_CODE = TechnicalRole.SPACE_ADMIN.code();

    private final SpaceContextVerifier spaceContextVerifier;
    private final SpaceBoundaryGuard spaceBoundaryGuard;
    private final CurrentAccountContextCase currentAccountContext;
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final GovernanceRoleAssignmentRepository assignments;
    private final UserRbacGovernanceMapper mapper;

    @Override
    @Transactional(readOnly = true)
    public UserRoleAssignmentsResponse listDirectRoles(ResolvedSpaceKey key, UUID userId) {
        guard(key);
        User user = requireUserInSpace(key, userId);
        return currentState(key, user);
    }

    @Override
    public UserRoleAssignmentsResponse assignRole(ResolvedSpaceKey key, AssignUserRoleCommand command) {
        guard(key);
        UUID actorAccountId = currentAccountContext.requireCurrentAccountId();
        User user = requireUserInSpace(key, command.userId());
        ResolvedRole role = resolveGovernableRole(key, command.roleCode());
        if (role.source() != RoleSource.TECHNICAL) {
            TenantRoleCodePolicy.requireTenantCode(role.code());
        }
        assertNoScopeEscalation(key, actorAccountId, role);

        UUID targetAccountId = user.getAccountId().getValue();
        if (!assignments.existsAssignment(key.orgId(), key.spaceId(), targetAccountId, role.code())) {
            try {
                // IAM 31 : le niveau de la ligne suit le scope du code — un pouvoir
                // ORGANIZATION est org-level (space NULL), jamais situé dans un space.
                UUID assignmentSpaceId =
                        role.plan() == AuthorityPlan.ORGANIZATION ? null : key.spaceId();
                assignments.saveGovernanceAssignment(new RoleAssignment(
                        null, key.orgId(), assignmentSpaceId,
                        new Identity(IdentityType.ACCOUNT, targetAccountId),
                        role.code(), role.source(), null,
                        Instant.now(), actorAccountId.toString(), null, null));
                log.info("Role assigned roleCode={} userId={} spaceId={} actorAccountId={} reason={}",
                        role.code(), user.getId().value(), assignmentSpaceId, actorAccountId, command.reason());
            } catch (DuplicateAssignmentException e) {
                log.debug("Role already assigned (concurrent), idempotent roleCode={} userId={}",
                        role.code(), user.getId().value());
            }
        }
        return currentState(key, user);
    }

    @Override
    public UserRoleAssignmentsResponse removeRole(ResolvedSpaceKey key, RemoveUserRoleCommand command) {
        guard(key);
        UUID actorAccountId = currentAccountContext.requireCurrentAccountId();
        User user = requireUserInSpace(key, command.userId());
        ResolvedRole role = resolveGovernableRole(key, command.roleCode());
        assertNoScopeEscalation(key, actorAccountId, role);

        UUID targetAccountId = user.getAccountId().getValue();
        if (!assignments.existsAssignment(key.orgId(), key.spaceId(), targetAccountId, role.code())) {
            return currentState(key, user);
        }

        if (ADMIN_ROLE_CODES.contains(role.code()) && targetAccountId.equals(actorAccountId)) {
            throw new SelfDemotionException(
                    "An administrator cannot remove their own " + role.code() + " on this surface");
        }
        if (SPACE_ADMIN_CODE.equals(role.code())
                && assignments.countIdentitiesHoldingRole(key.orgId(), key.spaceId(), SPACE_ADMIN_CODE) <= 1) {
            throw new LastAdminRemovalException(
                    "Cannot remove the last " + SPACE_ADMIN_CODE + " of the space");
        }

        // IAM 31 : le retrait cible le niveau où la ligne vit réellement.
        int deleted = role.plan() == AuthorityPlan.ORGANIZATION
                ? assignments.deleteOrgLevelAssignment(key.orgId(), targetAccountId, role.code())
                : assignments.deleteAssignment(key.orgId(), key.spaceId(), targetAccountId, role.code());
        log.info("Role removed roleCode={} userId={} spaceId={} actorAccountId={} deleted={} reason={}",
                role.code(), user.getId().value(), key.spaceId(), actorAccountId, deleted, command.reason());

        return currentState(key, user);
    }

    // ─────────────────────────── gardes ───────────────────────────

    private void guard(ResolvedSpaceKey key) {
        spaceContextVerifier.validateSpaceContext(key.spaceId());
        spaceBoundaryGuard.assertTokenMatches(key);
    }

    /** Un user hors du space courant N'EXISTE PAS (404) — jamais de 403 révélateur. */
    private User requireUserInSpace(ResolvedSpaceKey key, UUID userId) {
        return userRepository.findById(UserId.of(userId))
                .filter(user -> user.getSpaceId().equals(SpaceId.of(key.spaceId())))
                .orElseThrow(() -> new UserNotFoundException("User not found in this space: " + userId));
    }

    /**
     * Classe le code demandé dans le catalogue gouvernable de ce space.
     * SYSTEM/USER : n'existent pas pour les tenants (404). BUSINESS : interdit
     * sur cette surface (403). Inconnu : 404.
     */
    private ResolvedRole resolveGovernableRole(ResolvedSpaceKey key, String roleCode) {
        Optional<TechnicalRole> technical = TechnicalRole.fromCode(roleCode);
        if (technical.isPresent()) {
            TechnicalRole role = technical.get();
            if (role.selfService() || !ASSIGNABLE_PLANS.contains(role.plan())) {
                throw new RoleNotFoundException("Role not found in this space: " + roleCode);
            }
            return new ResolvedRole(role.code(), RoleSource.TECHNICAL, role.plan());
        }

        return roleRepository.findBySpaceIdAndCode(SpaceId.of(key.spaceId()), roleCode)
                .map(dbRole -> {
                    if (dbRole.getNature() == RoleNature.BUSINESS) {
                        throw new RoleTypeNotAllowedException(
                                "Business role " + roleCode + " is not assignable on the governance surface");
                    }
                    return new ResolvedRole(dbRole.getCode(), RoleSource.GOVERNANCE, AuthorityPlan.SPACE);
                })
                .orElseThrow(() -> new RoleNotFoundException("Role not found in this space: " + roleCode));
    }

    /**
     * On ne délègue (ni ne retire) jamais au-dessus de son propre scope : un pouvoir
     * ORGANIZATION exige une autorité ORGANIZATION réelle (état DB, pas le token).
     */
    private void assertNoScopeEscalation(ResolvedSpaceKey key, UUID actorAccountId, ResolvedRole role) {
        if (role.plan() != AuthorityPlan.ORGANIZATION) {
            return;
        }
        Set<String> actorCodes = Set.copyOf(
                assignments.findAssignedTechnicalRoleCodes(key.orgId(), key.spaceId(), actorAccountId));
        if (Collections.disjoint(actorCodes, ORG_AUTHORITY_ROLE_CODES)) {
            throw new RoleScopeEscalationException(
                    "Delegating or revoking " + role.code() + " requires organization-level authority");
        }
    }

    private UserRoleAssignmentsResponse currentState(ResolvedSpaceKey key, User user) {
        return mapper.toRolesResponse(
                user.getId().value(),
                assignments.findDirectAssignments(key.orgId(), key.spaceId(), user.getAccountId().getValue()));
    }

    private record ResolvedRole(String code, RoleSource source, AuthorityPlan plan) {}
}
