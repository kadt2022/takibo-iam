package com.takibo.identitycore.application.rbac.effective.service;

import com.takibo.identitycore.application.rbac.effective.model.EffectiveRbac;
import com.takibo.identitycore.domain.rbac.model.GroupAssignment;
import com.takibo.identitycore.domain.rbac.model.GroupSource;
import com.takibo.identitycore.domain.rbac.model.RoleAssignment;
import com.takibo.identitycore.domain.rbac.model.RoleSource;
import com.takibo.identitycore.domain.rbac.repository.GovernanceGroupAssignmentRepository;
import com.takibo.identitycore.domain.rbac.repository.GovernanceRoleAssignmentRepository;
import com.takibo.identitycore.domain.repository.GroupRoleRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EffectiveRbacQueryServiceTest {

    private static final UUID ORG_ID = UUID.fromString("aaaaaaaa-0000-0000-0000-000000000001");
    private static final UUID SPACE_ID = UUID.fromString("bbbbbbbb-0000-0000-0000-000000000002");
    private static final UUID ACCOUNT_ID = UUID.fromString("cccccccc-0000-0000-0000-000000000003");

    @Mock private GovernanceRoleAssignmentRepository roleAssignments;
    @Mock private GovernanceGroupAssignmentRepository groupMemberships;
    @Mock private GroupRoleRepository groupRoles;

    @InjectMocks
    private EffectiveRbacQueryService service;

    private RoleAssignment directRole(String code, RoleSource source) {
        return new RoleAssignment(UUID.randomUUID(), ORG_ID, SPACE_ID, null,
                code, source, null, Instant.now(), "actor", null, null);
    }

    private GroupAssignment membership(String code, GroupSource source) {
        return new GroupAssignment(UUID.randomUUID(), ORG_ID, SPACE_ID,
                ACCOUNT_ID, null, null, code, source, null,
                Instant.now(), "actor", null, null);
    }

    private void givenDirectRoles(RoleAssignment... assignments) {
        when(roleAssignments.findDirectAssignments(ORG_ID, SPACE_ID, ACCOUNT_ID))
                .thenReturn(List.of(assignments));
    }

    private void givenMemberships(GroupAssignment... memberships) {
        when(groupMemberships.findDirectMemberships(ORG_ID, SPACE_ID, ACCOUNT_ID))
                .thenReturn(List.of(memberships));
    }

    private EffectiveRbac effective() {
        return service.effectiveFor(ORG_ID, SPACE_ID, ACCOUNT_ID);
    }

    @Test
    void directRoleOnly_yieldsRoleAndDerivedTechnicalPermissions() {
        givenDirectRoles(directRole("R_SPACE_ADMIN", RoleSource.TECHNICAL));
        givenMemberships();

        EffectiveRbac rbac = effective();

        assertThat(rbac.roles()).containsExactly("R_SPACE_ADMIN");
        assertThat(rbac.groups()).isEmpty();
        assertThat(rbac.permissions()).contains("P_MANAGE_USERS", "P_ASSIGN_ROLES", "P_READ_POLICY");
    }

    @Test
    void technicalGroupOnly_transmitsItsRolesAndTheirPermissions() {
        // L'appartenance transmet réellement un pouvoir : G_SPACE_ADMINS -> R_SPACE_ADMIN.
        givenDirectRoles();
        givenMemberships(membership("G_SPACE_ADMINS", GroupSource.TECHNICAL));

        EffectiveRbac rbac = effective();

        assertThat(rbac.groups()).containsExactly("G_SPACE_ADMINS");
        assertThat(rbac.roles()).containsExactly("R_SPACE_ADMIN");
        assertThat(rbac.permissions()).contains("P_MANAGE_USERS");
    }

    @Test
    void governanceDbGroup_transmitsLinkedGovernanceRoles() {
        givenDirectRoles();
        givenMemberships(membership("GRP_LOCAL_ADMINS", GroupSource.GOVERNANCE));
        when(groupRoles.findGovernanceRoleCodesByGroups(ORG_ID, SPACE_ID, List.of("GRP_LOCAL_ADMINS")))
                .thenReturn(List.of("GOV_LOCAL"));

        EffectiveRbac rbac = effective();

        assertThat(rbac.groups()).containsExactly("GRP_LOCAL_ADMINS");
        assertThat(rbac.roles()).containsExactly("GOV_LOCAL");
        // GOV_LOCAL n'est pas un rôle technique : aucune permission dérivée.
        assertThat(rbac.permissions()).isEmpty();
    }

    @Test
    void directAndInheritedOverlap_isDeduplicatedAndSorted() {
        givenDirectRoles(
                directRole("R_SPACE_ADMIN", RoleSource.TECHNICAL),
                directRole("GOV_LOCAL", RoleSource.GOVERNANCE));
        givenMemberships(membership("G_SPACE_ADMINS", GroupSource.TECHNICAL));

        EffectiveRbac rbac = effective();

        // R_SPACE_ADMIN direct ET hérité de G_SPACE_ADMINS : une seule occurrence, tri par code.
        assertThat(rbac.roles()).containsExactly("GOV_LOCAL", "R_SPACE_ADMIN");
        assertThat(rbac.permissions()).isSorted().doesNotHaveDuplicates();
    }

    @Test
    void emptyRbac_yieldsThreeEmptyClaims() {
        givenDirectRoles();
        givenMemberships();

        EffectiveRbac rbac = effective();

        assertThat(rbac.roles()).isEmpty();
        assertThat(rbac.groups()).isEmpty();
        assertThat(rbac.permissions()).isEmpty();
        verify(groupRoles, never()).findGovernanceRoleCodesByGroups(any(), any(), anyCollection());
    }

    @Test
    void hiddenTechnicalCodes_neverEnterTenantClaims_evenFromSeededRows() {
        // Une ligne seedée R_TAKIBO_PLATFORM_ADMIN / R_SELF ne fuit jamais dans un token tenant.
        givenDirectRoles(
                directRole("R_TAKIBO_PLATFORM_ADMIN", RoleSource.TECHNICAL),
                directRole("R_SELF", RoleSource.TECHNICAL),
                directRole("R_SPACE_ADMIN", RoleSource.TECHNICAL));
        givenMemberships();

        EffectiveRbac rbac = effective();

        assertThat(rbac.roles()).containsExactly("R_SPACE_ADMIN");
        assertThat(rbac.permissions()).doesNotContain("P_CREATE_ORG", "P_DELETE_ORG");
    }

    @Test
    void businessAssignments_areIgnoredByEffectiveComputation() {
        // Le contrat repository exclut déjà BUSINESS ; si une ligne difforme passait
        // quand même (code null, businessRoleId porté), elle n'entre pas dans le token.
        RoleAssignment businessShaped = new RoleAssignment(UUID.randomUUID(), ORG_ID, SPACE_ID, null,
                null, RoleSource.BUSINESS, UUID.randomUUID(), Instant.now(), "actor", null, null);
        givenDirectRoles(businessShaped, directRole("R_SPACE_VIEWER", RoleSource.TECHNICAL));
        givenMemberships();

        EffectiveRbac rbac = effective();

        assertThat(rbac.roles()).containsExactly("R_SPACE_VIEWER");
    }

    @Test
    void orgScopedTechnicalGroup_transmitsOrgRoles() {
        givenDirectRoles();
        givenMemberships(membership("G_ORG_ADMINS", GroupSource.TECHNICAL));

        EffectiveRbac rbac = effective();

        assertThat(rbac.groups()).containsExactly("G_ORG_ADMINS");
        assertThat(rbac.roles()).containsExactly("R_ORG_ADMIN", "R_ORG_OWNER");
        assertThat(rbac.permissions()).contains("P_CREATE_SPACE", "P_UPDATE_POLICY");
    }

    @Test
    void permissions_deriveOnlyFromTenantVisibleTechnicalRoles() {
        givenDirectRoles(directRole("R_ORG_AUDITOR", RoleSource.TECHNICAL));
        givenMemberships();

        EffectiveRbac rbac = effective();

        assertThat(rbac.permissions()).containsExactly(
                "P_EXPORT_AUDIT_LOGS", "P_READ_AUDIT_LOGS", "P_READ_ORG", "P_READ_POLICY");
    }

    // ───────────────────── IAM 31 — effectiveOrgFor (portée ORGANIZATION) ─────────────────────

    private RoleAssignment orgLevelRole(String code) {
        return new RoleAssignment(UUID.randomUUID(), ORG_ID, null, null,
                code, RoleSource.TECHNICAL, null, Instant.now(), "actor", null, null);
    }

    private GroupAssignment orgLevelMembership(String code) {
        return new GroupAssignment(UUID.randomUUID(), ORG_ID, null,
                ACCOUNT_ID, null, null, code, GroupSource.TECHNICAL, null,
                Instant.now(), "actor", null, null);
    }

    private void givenOrgLevelRoles(RoleAssignment... assignments) {
        when(roleAssignments.findOrgLevelAssignments(ORG_ID, ACCOUNT_ID))
                .thenReturn(List.of(assignments));
    }

    private void givenOrgLevelMemberships(GroupAssignment... memberships) {
        when(groupMemberships.findOrgLevelMemberships(ORG_ID, ACCOUNT_ID))
                .thenReturn(List.of(memberships));
    }

    @Test
    void orgScope_directOrgRole_yieldsOrgRolesAndOrgPermissionsOnly() {
        givenOrgLevelRoles(orgLevelRole("R_ORG_OWNER"));
        givenOrgLevelMemberships();

        EffectiveRbac rbac = service.effectiveOrgFor(ORG_ID, ACCOUNT_ID);

        assertThat(rbac.roles()).containsExactly("R_ORG_OWNER");
        assertThat(rbac.permissions()).contains("P_READ_ORG", "P_CREATE_SPACE", "P_ASSIGN_ROLES");
        // I2 : aucun code de scope SYSTEM/SPACE/USER.
        assertThat(rbac.permissions()).doesNotContain("P_CREATE_ORG", "P_DELETE_ORG");
    }

    @Test
    void orgScope_orgGroup_transmitsOrgRoles() {
        givenOrgLevelRoles();
        givenOrgLevelMemberships(orgLevelMembership("G_ORG_ADMINS"));

        EffectiveRbac rbac = service.effectiveOrgFor(ORG_ID, ACCOUNT_ID);

        assertThat(rbac.groups()).containsExactly("G_ORG_ADMINS");
        assertThat(rbac.roles()).containsExactly("R_ORG_ADMIN", "R_ORG_OWNER");
    }

    @Test
    void orgScope_anomalousOrgLevelSpaceRole_isIgnoredOnRead() {
        // AC-06 : garde de lecture — une ligne org-level anormale (code SPACE ou
        // inconnu) n'entre pas dans un token ORG, même si l'écriture l'a laissée passer.
        givenOrgLevelRoles(
                orgLevelRole("R_SPACE_ADMIN"),
                orgLevelRole("GOV_LOCAL"),
                orgLevelRole("R_ORG_VIEWER"));
        givenOrgLevelMemberships(orgLevelMembership("G_SPACE_ADMINS"));

        EffectiveRbac rbac = service.effectiveOrgFor(ORG_ID, ACCOUNT_ID);

        assertThat(rbac.roles()).containsExactly("R_ORG_VIEWER");
        assertThat(rbac.groups()).isEmpty();
        assertThat(rbac.permissions()).containsExactly("P_READ_ORG", "P_READ_POLICY");
    }

    @Test
    void orgScope_noOrgAuthority_yieldsEmptyClaims() {
        // AC-11 : claims vides = login OK, la découverte des spaces viendra d'IAM 32.
        givenOrgLevelRoles();
        givenOrgLevelMemberships();

        EffectiveRbac rbac = service.effectiveOrgFor(ORG_ID, ACCOUNT_ID);

        assertThat(rbac.roles()).isEmpty();
        assertThat(rbac.groups()).isEmpty();
        assertThat(rbac.permissions()).isEmpty();
    }

    @Test
    void orgScope_neverTouchesSpaceSituatedReads() {
        // I4 : le pouvoir ORG ne dépend d'aucun space — ni lecture située, ni group_roles.
        givenOrgLevelRoles(orgLevelRole("R_ORG_ADMIN"));
        givenOrgLevelMemberships();

        service.effectiveOrgFor(ORG_ID, ACCOUNT_ID);

        verify(roleAssignments, never()).findDirectAssignments(any(), any(), any());
        verify(groupMemberships, never()).findDirectMemberships(any(), any(), any());
        verify(groupRoles, never()).findGovernanceRoleCodesByGroups(any(), any(), anyCollection());
    }
}
