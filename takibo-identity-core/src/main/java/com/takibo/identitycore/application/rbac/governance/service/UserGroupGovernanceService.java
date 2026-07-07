package com.takibo.identitycore.application.rbac.governance.service;

import com.takibo.identitycore.application.rbac.governance.command.AddUserToGroupCommand;
import com.takibo.identitycore.application.rbac.governance.command.RemoveUserFromGroupCommand;
import com.takibo.identitycore.application.rbac.governance.mapper.UserRbacGovernanceMapper;
import com.takibo.identitycore.application.rbac.governance.port.in.UserGroupGovernanceCase;
import com.takibo.identitycore.domain.catalogrbac.TechnicalGroup;
import com.takibo.identitycore.domain.catalogrbac.TechnicalScope;
import com.takibo.identitycore.domain.exception.DuplicateAssignmentException;
import com.takibo.identitycore.domain.exception.GroupNotFoundException;
import com.takibo.identitycore.domain.exception.GroupTypeNotAllowedException;
import com.takibo.identitycore.domain.exception.LastAdminRemovalException;
import com.takibo.identitycore.domain.exception.RoleScopeEscalationException;
import com.takibo.identitycore.domain.exception.SelfDemotionException;
import com.takibo.identitycore.domain.exception.UserNotFoundException;
import com.takibo.identitycore.domain.model.GroupNature;
import com.takibo.identitycore.domain.model.Identity;
import com.takibo.identitycore.domain.model.IdentityType;
import com.takibo.identitycore.domain.model.User;
import com.takibo.identitycore.domain.rbac.model.GroupAssignment;
import com.takibo.identitycore.domain.rbac.model.GroupSource;
import com.takibo.identitycore.domain.rbac.repository.GovernanceGroupAssignmentRepository;
import com.takibo.identitycore.domain.rbac.repository.GovernanceRoleAssignmentRepository;
import com.takibo.identitycore.domain.repository.GroupRepository;
import com.takibo.identitycore.domain.repository.UserRepository;
import com.takibo.identitycore.domain.vo.SpaceId;
import com.takibo.identitycore.domain.vo.UserId;
import com.takibo.identitycore.integration.security.SpaceBoundaryGuard;
import com.takibo.identitycore.integration.security.port.CurrentAccountContextCase;
import com.takibo.identitycore.integration.space.SpaceContextVerifier;
import com.takibo.identitycore.integration.space.port.ResolvedSpaceKey;
import com.takibo.identitycore.interfaces.rest.response.UserGroupMembershipsResponse;
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
 * Gouvernance des memberships directs d'un user situé. Le groupe transmet le
 * pouvoir par appartenance : l'ajouter ou le retirer suit les mêmes gardes que la
 * délégation de rôle — frontière, catalogue visible, non-escalade verticale,
 * self-demotion et dernier chemin admin du space.
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class UserGroupGovernanceService implements UserGroupGovernanceCase {

    private static final Set<TechnicalScope> ASSIGNABLE_SCOPES =
            EnumSet.of(TechnicalScope.ORGANIZATION, TechnicalScope.SPACE);

    /** Autorité requise pour gérer un membership de scope ORGANIZATION. */
    private static final Set<String> ORG_AUTHORITY_ROLE_CODES = Set.of("R_ORG_OWNER", "R_ORG_ADMIN");

    /** Groupes d'administration : jamais auto-quittés sur cette surface. */
    private static final Set<String> ADMIN_GROUP_CODES = Set.of("G_ORG_ADMINS", "G_SPACE_ADMINS");

    private static final String SPACE_ADMINS_CODE = TechnicalGroup.SPACE_ADMINS.code();

    private final SpaceContextVerifier spaceContextVerifier;
    private final SpaceBoundaryGuard spaceBoundaryGuard;
    private final CurrentAccountContextCase currentAccountContext;
    private final UserRepository userRepository;
    private final GroupRepository groupRepository;
    private final GovernanceGroupAssignmentRepository memberships;
    private final GovernanceRoleAssignmentRepository roleAssignments;
    private final UserRbacGovernanceMapper mapper;

    @Override
    @Transactional(readOnly = true)
    public UserGroupMembershipsResponse listDirectGroups(ResolvedSpaceKey key, UUID userId) {
        guard(key);
        User user = requireUserInSpace(key, userId);
        return currentState(key, user);
    }

    @Override
    public UserGroupMembershipsResponse addToGroup(ResolvedSpaceKey key, AddUserToGroupCommand command) {
        guard(key);
        UUID actorAccountId = currentAccountContext.requireCurrentAccountId();
        User user = requireUserInSpace(key, command.userId());
        ResolvedGroup group = resolveGovernableGroup(key, command.groupCode());
        assertNoScopeEscalation(key, actorAccountId, group);

        UUID targetAccountId = user.getAccountId().getValue();
        if (!memberships.existsMembership(key.orgId(), key.spaceId(), targetAccountId, group.code())) {
            try {
                memberships.saveGovernanceAssignment(new GroupAssignment(
                        null, key.orgId(), key.spaceId(),
                        targetAccountId, new Identity(IdentityType.ACCOUNT, targetAccountId),
                        IdentityType.ACCOUNT,
                        group.code(), group.source(), null,
                        Instant.now(), actorAccountId.toString(), null, null));
                log.info("Group membership added groupCode={} userId={} spaceId={} actorAccountId={} reason={}",
                        group.code(), user.getId().value(), key.spaceId(), actorAccountId, command.reason());
            } catch (DuplicateAssignmentException e) {
                log.debug("Membership already exists (concurrent), idempotent groupCode={} userId={}",
                        group.code(), user.getId().value());
            }
        }
        return currentState(key, user);
    }

    @Override
    public UserGroupMembershipsResponse removeFromGroup(ResolvedSpaceKey key, RemoveUserFromGroupCommand command) {
        guard(key);
        UUID actorAccountId = currentAccountContext.requireCurrentAccountId();
        User user = requireUserInSpace(key, command.userId());
        ResolvedGroup group = resolveGovernableGroup(key, command.groupCode());
        assertNoScopeEscalation(key, actorAccountId, group);

        UUID targetAccountId = user.getAccountId().getValue();
        if (!memberships.existsMembership(key.orgId(), key.spaceId(), targetAccountId, group.code())) {
            return currentState(key, user);
        }

        if (ADMIN_GROUP_CODES.contains(group.code()) && targetAccountId.equals(actorAccountId)) {
            throw new SelfDemotionException(
                    "An administrator cannot leave " + group.code() + " by themselves on this surface");
        }
        if (SPACE_ADMINS_CODE.equals(group.code())
                && memberships.countIdentitiesInGroup(key.orgId(), key.spaceId(), SPACE_ADMINS_CODE) <= 1) {
            throw new LastAdminRemovalException(
                    "Cannot remove the last member of " + SPACE_ADMINS_CODE + " of the space");
        }

        int deleted = memberships.deleteMembership(key.orgId(), key.spaceId(), targetAccountId, group.code());
        log.info("Group membership removed groupCode={} userId={} spaceId={} actorAccountId={} deleted={} reason={}",
                group.code(), user.getId().value(), key.spaceId(), actorAccountId, deleted, command.reason());

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
     * Scope non visible tenant : n'existe pas (404). BUSINESS : interdit (403).
     */
    private ResolvedGroup resolveGovernableGroup(ResolvedSpaceKey key, String groupCode) {
        Optional<TechnicalGroup> technical = TechnicalGroup.fromCode(groupCode);
        if (technical.isPresent()) {
            TechnicalGroup group = technical.get();
            if (!ASSIGNABLE_SCOPES.contains(group.scope())) {
                throw new GroupNotFoundException("Group not found in this space: " + groupCode);
            }
            return new ResolvedGroup(group.code(), GroupSource.TECHNICAL, group.scope());
        }

        return groupRepository.findBySpaceIdAndCode(SpaceId.of(key.spaceId()), groupCode)
                .map(dbGroup -> {
                    if (dbGroup.getNature() == GroupNature.BUSINESS) {
                        throw new GroupTypeNotAllowedException(
                                "Business group " + groupCode + " does not accept memberships"
                                        + " on the governance surface");
                    }
                    return new ResolvedGroup(dbGroup.getCode(), GroupSource.GOVERNANCE, TechnicalScope.SPACE);
                })
                .orElseThrow(() -> new GroupNotFoundException("Group not found in this space: " + groupCode));
    }

    /**
     * On ne transmet (ni ne retire) jamais un pouvoir au-dessus de son propre scope :
     * un membership ORGANIZATION exige une autorité ORGANIZATION réelle (état DB).
     */
    private void assertNoScopeEscalation(ResolvedSpaceKey key, UUID actorAccountId, ResolvedGroup group) {
        if (group.scope() != TechnicalScope.ORGANIZATION) {
            return;
        }
        Set<String> actorCodes = Set.copyOf(
                roleAssignments.findAssignedTechnicalRoleCodes(key.orgId(), key.spaceId(), actorAccountId));
        if (Collections.disjoint(actorCodes, ORG_AUTHORITY_ROLE_CODES)) {
            throw new RoleScopeEscalationException(
                    "Managing membership of " + group.code() + " requires organization-level authority");
        }
    }

    private UserGroupMembershipsResponse currentState(ResolvedSpaceKey key, User user) {
        return mapper.toGroupsResponse(
                user.getId().value(),
                memberships.findDirectMemberships(key.orgId(), key.spaceId(), user.getAccountId().getValue()));
    }

    private record ResolvedGroup(String code, GroupSource source, TechnicalScope scope) {}
}
