package com.takibo.iamboot.security;

import com.takibo.iamboot.support.TokenPermissions;
import com.takibo.identitycore.application.identity.service.UserRegistrationOrchestrator;
import com.takibo.identitycore.domain.exception.SpaceNotFoundException;
import com.takibo.identitycore.domain.exception.UserCreationException;
import com.takibo.identitycore.domain.status.SpaceOperationalStatus;
import com.takibo.identitycore.integration.space.port.ResolvedSpaceKey;
import com.takibo.identitycore.integration.space.port.SpaceKeyResolutionCase;
import com.takibo.identitycore.integration.space.port.SpaceManagementCase;
import com.takibo.identitycore.integration.space.port.SpaceStatusCheckerCase;
import com.takibo.managementservice.infrastructure.jpa.repository.SpaceOwnershipRepository;
import com.takibo.securitymanagement.infrastructure.security.boundary.BoundaryMembershipService;
import com.takibo.securitymanagement.infrastructure.token.TokenValidatorAdapter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Frontières de la création d'utilisateur par la route lisible
 * {@code POST /api/v1/orgs/{orgCode}/spaces/{spaceCode}/users}, à travers la VRAIE chaîne
 * Spring Security puis les vraies gardes applicatives (UserApplicationService,
 * SpaceOwnershipGuard, SpaceContextVerifier). Elles remplacent les preuves portées par la
 * route historique {@code POST /api/spaces/{spaceId}/users}, supprimée.
 * <p>
 * Seuls la validation cryptographique du token et les accès aux données (résolution des
 * codes, propriété et statut des spaces) sont doublés. L'orchestrateur d'inscription est
 * observé : chaque refus prouve qu'il n'est jamais atteint. Le témoin légitime l'atteint et
 * s'y arrête sur une erreur métier, sans rien créer.
 */
@SpringBootTest(properties = "takibo.adp.enabled=false")
@ActiveProfiles("test")
class ReadableUserCreationBoundaryHttpIntegrationTest {

    private static final UUID ORG_ID = UUID.fromString("aaaaaaaa-0000-0000-0000-000000000001");
    private static final UUID OTHER_ORG_ID = UUID.fromString("99999999-0000-0000-0000-000000000009");
    private static final UUID SPACE_ID = UUID.fromString("bbbbbbbb-0000-0000-0000-000000000002");
    private static final UUID OTHER_SPACE_ID = UUID.fromString("cccccccc-0000-0000-0000-000000000003");
    private static final UUID FOREIGN_SPACE_ID = UUID.fromString("dddddddd-0000-0000-0000-000000000004");
    private static final UUID USER_ID = UUID.fromString("eeeeeeee-0000-0000-0000-000000000005");

    private static final String USERS = "/api/v1/orgs/acme/spaces/finance/users";
    private static final String OTHER_SPACE_USERS = "/api/v1/orgs/acme/spaces/operations/users";
    private static final String UNKNOWN_SPACE_USERS = "/api/v1/orgs/acme/spaces/inexistant/users";
    private static final String LEGACY_USERS = "/api/spaces/" + SPACE_ID + "/users";

    private static final String BODY = """
            {"email":"frontiere@takibo.io","rawPassword":"An0ther!Passw0rd",
             "username":"frontiere","firstName":"Frontiere","lastName":"Membership"}""";

    @Autowired
    private WebApplicationContext context;

    @MockitoBean
    private TokenValidatorAdapter tokenValidatorAdapter;

    @MockitoBean
    private SpaceKeyResolutionCase spaceKeyResolution;

    @MockitoBean
    private SpaceOwnershipRepository spaceOwnershipRepository;

    @MockitoBean
    private SpaceStatusCheckerCase spaceStatusChecker;

    @MockitoBean
    private SpaceManagementCase spaceManagement;

    /**
     * OrgBoundaryFilter (hors périmètre, inchangé) reconnaît encore le chemin historique
     * /api/spaces/{spaceId}/** et y interroge l'appartenance en SQL PostgreSQL. Doublé ici
     * (accepte) pour isoler le refus par défaut de la chaîne API ; le BVT le prouve sur
     * PostgreSQL avec le service réel.
     */
    @MockitoBean
    private BoundaryMembershipService boundaryMembershipService;

    @MockitoSpyBean
    private UserRegistrationOrchestrator registrationOrchestrator;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() throws Exception {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();

        when(spaceKeyResolution.resolve("acme", "finance"))
                .thenReturn(new ResolvedSpaceKey(ORG_ID, SPACE_ID, "acme", "finance"));
        when(spaceKeyResolution.resolve("acme", "operations"))
                .thenReturn(new ResolvedSpaceKey(ORG_ID, OTHER_SPACE_ID, "acme", "operations"));
        when(spaceKeyResolution.resolve("acme", "inexistant"))
                .thenThrow(new SpaceNotFoundException("Space not found: inexistant in organization acme"));

        when(spaceOwnershipRepository.findOrgIdBySpaceId(SPACE_ID)).thenReturn(Optional.of(ORG_ID));
        when(spaceOwnershipRepository.findOrgIdBySpaceId(OTHER_SPACE_ID)).thenReturn(Optional.of(ORG_ID));
        when(spaceManagement.findOrgIdBySpaceId(any())).thenReturn(Optional.of(ORG_ID));
        when(spaceStatusChecker.findStatus(any())).thenReturn(Optional.of(SpaceOperationalStatus.ACTIVE));
        // Méthode par défaut du port : sa vraie logique (404 inexistant, refus si non ACTIVE) est conservée.
        doCallRealMethod().when(spaceStatusChecker).assertSpaceExistsAndActive(any());

        token("founder", claims(ORG_ID, SPACE_ID, "SPACE", "R_ORG_OWNER"));
        token("founder-org", claims(ORG_ID, null, "ORGANIZATION", "R_ORG_OWNER"));
        token("other-org-admin", claims(OTHER_ORG_ID, FOREIGN_SPACE_ID, "SPACE", "R_ORG_ADMIN"));
        token("other-space-admin", claims(ORG_ID, OTHER_SPACE_ID, "SPACE", "R_SPACE_ADMIN"));
        token("member", claims(ORG_ID, SPACE_ID, "SPACE"));
        // Token d'une autre organisation qui revendique le space cible : seul l'org_id diffère.
        token("other-org-claiming-space", claims(OTHER_ORG_ID, SPACE_ID, "SPACE", "R_ORG_ADMIN"));
    }

    private void token(String name, Map<String, Object> claims) throws Exception {
        when(tokenValidatorAdapter.validate(name)).thenReturn(claims);
    }

    private static Map<String, Object> claims(UUID orgId, UUID spaceId, String scopeLevel, String... roles) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("sub", USER_ID.toString());
        claims.put("subjectType", "HUMAN");
        claims.put("authMethod", "PASSWORD");
        claims.put("userId", USER_ID.toString());
        claims.put("orgId", orgId.toString());
        if (spaceId != null) {
            claims.put("spaceId", spaceId.toString());
        }
        claims.put("scopeLevel", scopeLevel);
        claims.put("roles", List.of(roles));
        claims.put("permissions", TokenPermissions.of(List.of(roles), orgId.toString(),
                spaceId == null ? null : spaceId.toString(), scopeLevel));
        return claims;
    }

    private ResultActions create(String path, String token) throws Exception {
        var request = post(path).contentType(MediaType.APPLICATION_JSON).content(BODY);
        if (token != null) {
            request.header("Authorization", "Bearer " + token);
        }
        return mockMvc.perform(request);
    }

    // ── Témoin : le titulaire légitime franchit toutes les gardes ──────

    @Test
    void founder_inItsSpace_reachesRegistration() throws Exception {
        doThrow(new UserCreationException("témoin : arrêt avant toute écriture"))
                .when(registrationOrchestrator).registerUser(any());

        create(USERS, "founder")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("USER_CREATION_ERROR"));

        verify(registrationOrchestrator, times(1)).registerUser(any());
    }

    // ── Refus : l'inscription n'est jamais atteinte ───────────────────

    @Test
    void otherOrganizationToken_isForbidden_andRegistersNothing() throws Exception {
        assertForbiddenWithoutRegistration(create(USERS, "other-org-admin"));
    }

    @Test
    void otherOrganizationTokenClaimingTheTargetSpace_isForbidden_andRegistersNothing() throws Exception {
        assertForbiddenWithoutRegistration(create(USERS, "other-org-claiming-space"));
    }

    @Test
    void organizationScopedToken_isForbidden_andRegistersNothing() throws Exception {
        assertForbiddenWithoutRegistration(create(USERS, "founder-org"));
    }

    @Test
    void tokenOfAnotherSpaceOfTheSameOrganization_isForbidden_andRegistersNothing() throws Exception {
        assertForbiddenWithoutRegistration(create(USERS, "other-space-admin"));
    }

    @Test
    void ownerTokenOfSpaceA_cannotCreateInSpaceB_andRegistersNothing() throws Exception {
        assertForbiddenWithoutRegistration(create(OTHER_SPACE_USERS, "founder"));
    }

    @Test
    void memberWithoutAdminRole_isForbidden_andRegistersNothing() throws Exception {
        assertForbiddenWithoutRegistration(create(USERS, "member"));
    }

    @Test
    void anonymous_isUnauthorized_andRegistersNothing() throws Exception {
        create(USERS, null)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_FAILED"));

        verify(registrationOrchestrator, never()).registerUser(any());
    }

    @Test
    void unknownSpace_isNotFound_andRegistersNothing() throws Exception {
        create(UNKNOWN_SPACE_USERS, "founder")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("SPACE_NOT_FOUND"));

        verify(registrationOrchestrator, never()).registerUser(any());
    }

    @Test
    void suspendedSpace_isForbidden_beforeAnyAccountOrUserIsCreated() throws Exception {
        when(spaceStatusChecker.findStatus(SPACE_ID)).thenReturn(Optional.of(SpaceOperationalStatus.SUSPENDED));

        create(USERS, "founder").andExpect(status().isForbidden());

        verify(registrationOrchestrator, times(1)).registerUser(any());
    }

    // ── Méthode sans endpoint : 403, jamais 405 (RBAC-05/06) ──────────
    // Avant RBAC-05/06, ces paires retombaient sur POL_DEFAULT_ALLOW et Spring MVC répondait
    // 405. Elles sont désormais refusées par la politique, comme sur les surfaces UUID
    // (dashboard, clients OAuth2, spaces) : un appelant n'apprend pas quelles méthodes existent.

    @Test
    void methodWithoutEndpoint_isForbidden_evenForTheSpaceOwner() throws Exception {
        String user = USERS + "/" + USER_ID;
        String catalog = "/api/v1/orgs/acme/spaces/finance";
        Object[][] pairs = {
                {HttpMethod.DELETE, user},
                {HttpMethod.PUT, USERS},
                {HttpMethod.POST, user + "/roles/R_SPACE_ADMIN"},
                {HttpMethod.PATCH, user + "/groups"},
                {HttpMethod.POST, catalog + "/roles"},
                {HttpMethod.DELETE, catalog + "/permissions/P_SPACE_READ"},
        };
        for (Object[] pair : pairs) {
            mockMvc.perform(request((HttpMethod) pair[0], (String) pair[1])
                            .header("Authorization", "Bearer founder")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
        }

        verify(registrationOrchestrator, never()).registerUser(any());
    }

    // ── Route historique supprimée ────────────────────────────────────

    @Test
    void legacyRoute_isDeniedByDefault_forAnAuthenticatedCaller() throws Exception {
        create(LEGACY_USERS, "founder")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));

        verify(registrationOrchestrator, never()).registerUser(any());
    }

    @Test
    void legacyRoute_isUnauthorized_withoutToken() throws Exception {
        create(LEGACY_USERS, null).andExpect(status().isUnauthorized());

        verify(registrationOrchestrator, never()).registerUser(any());
    }

    private void assertForbiddenWithoutRegistration(ResultActions result) throws Exception {
        result.andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));

        verify(registrationOrchestrator, never()).registerUser(any());
    }
}
