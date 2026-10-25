package com.takibo.iamboot.security;

import com.takibo.identitycore.domain.status.SpaceOperationalStatus;
import com.takibo.identitycore.integration.space.port.SpaceOwnershipGuardCase;
import com.takibo.identitycore.integration.space.port.SpaceStatusCheckerCase;
import com.takibo.managementservice.application.dashboard.OrgDashboardSummaryService;
import com.takibo.managementservice.application.dashboard.OrganizationDashboardSummaryResult;
import com.takibo.managementservice.application.query.port.SpaceQueryCase;
import com.takibo.managementservice.application.service.OAuthClientService;
import com.takibo.managementservice.application.service.SpaceApplicationService;
import com.takibo.managementservice.domain.model.OAuthClient;
import com.takibo.managementservice.domain.model.RegisteredClientResult;
import com.takibo.securitymanagement.infrastructure.token.TokenValidatorAdapter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Frontières tenant (organisation, space) de bout en bout, à travers la VRAIE chaîne Spring
 * Security (JwtAuthenticationFilter -> PolicyBasedAuthorizationManager -> PolicyEvaluator)
 * puis les gardes des contrôleurs. Complète SpaceSurfaceSecurityIntegrationTest (liste des
 * spaces cross-org, lecture d'un autre space) sur les surfaces qu'il ne couvre pas :
 * création et lecture de space, clients OAuth et dashboard.
 * <p>
 * Seuls la validation cryptographique du token et les services applicatifs sont doublés :
 * chaque refus prouve que le service n'est jamais atteint, et chaque témoin prouve que le
 * même montage laisse passer le titulaire légitime. L'ADP est neutralisé, comme en BVT,
 * pour que ces preuves ne dépendent pas de l'heure du runner.
 */
@SpringBootTest(properties = "takibo.adp.enabled=false")
@ActiveProfiles("test")
class TenantBoundaryHttpIntegrationTest {

    private static final UUID ORG_ID = UUID.fromString("aaaaaaaa-0000-0000-0000-000000000001");
    private static final UUID OTHER_ORG_ID = UUID.fromString("99999999-0000-0000-0000-000000000009");
    private static final UUID SPACE_ID = UUID.fromString("bbbbbbbb-0000-0000-0000-000000000002");
    private static final UUID OTHER_SPACE_ID = UUID.fromString("cccccccc-0000-0000-0000-000000000003");
    private static final UUID USER_ID = UUID.fromString("dddddddd-0000-0000-0000-000000000004");
    private static final UUID CLIENT_ID = UUID.fromString("eeeeeeee-0000-0000-0000-000000000005");

    private static final String SPACES = "/api/v1/orgs/" + ORG_ID + "/spaces";
    private static final String CLIENTS = SPACES + "/" + SPACE_ID + "/clients";
    private static final String ROTATE = CLIENTS + "/" + CLIENT_ID + "/rotate-secret";
    private static final String DASHBOARD = "/api/v1/orgs/" + ORG_ID + "/dashboard/summary";

    private static final String SPACE_BODY = "{\"name\":\"Busa\",\"code\":\"busa\"}";
    private static final String ROTATE_BODY = "{\"clientSecretExpiresAt\":\"2099-01-01T00:00:00Z\"}";
    private static final String CLIENT_BODY = """
            {"clientId":"billing-batch","clientName":"Billing batch",
             "clientType":"CONFIDENTIAL","grantTypes":["client_credentials"]}""";

    @Autowired
    private WebApplicationContext context;

    @MockitoBean
    private TokenValidatorAdapter tokenValidatorAdapter;

    @MockitoBean
    private SpaceQueryCase spaceQueryCase;

    @MockitoBean
    private SpaceApplicationService spaceApplicationService;

    @MockitoBean
    private OAuthClientService oauthClientService;

    @MockitoBean
    private OrgDashboardSummaryService dashboardService;

    @MockitoBean
    private SpaceStatusCheckerCase spaceStatusChecker;

    @MockitoBean
    private SpaceOwnershipGuardCase spaceOwnershipGuard;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() throws Exception {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        when(spaceStatusChecker.findStatus(any())).thenReturn(Optional.of(SpaceOperationalStatus.ACTIVE));

        token("org-admin", claims(ORG_ID, null, "ORGANIZATION", "R_ORG_ADMIN"));
        token("other-org-admin", claims(OTHER_ORG_ID, null, "ORGANIZATION", "R_ORG_ADMIN"));
        token("space-admin", claims(ORG_ID, SPACE_ID, "SPACE", "R_SPACE_ADMIN"));
        token("other-space-admin", claims(ORG_ID, OTHER_SPACE_ID, "SPACE", "R_SPACE_ADMIN"));
        // Token d'une autre organisation qui revendique le space cible : seul l'org_id diffère.
        token("other-org-space-admin", claims(OTHER_ORG_ID, SPACE_ID, "SPACE", "R_ORG_ADMIN"));
        token("space-scoped-org-admin", claims(ORG_ID, SPACE_ID, "SPACE", "R_ORG_ADMIN"));
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
        return claims;
    }

    private static String bearer(String token) {
        return "Bearer " + token;
    }

    // ── Spaces ──────────────────────────────────────────────────────────

    @Test
    void otherOrgAdmin_creatingSpace_isForbidden_andCreatesNothing() throws Exception {
        mockMvc.perform(post(SPACES)
                        .header("Authorization", bearer("other-org-admin"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(SPACE_BODY))
                .andExpect(status().isForbidden());

        verifyNoInteractions(spaceApplicationService);
    }

    @Test
    void otherOrgAdmin_readingSpace_isForbidden_andReadsNothing() throws Exception {
        mockMvc.perform(get(SPACES + "/" + SPACE_ID)
                        .header("Authorization", bearer("other-org-admin")))
                .andExpect(status().isForbidden());

        verify(spaceQueryCase, never()).getSpace(any(), any());
    }

    // ── Clients OAuth ───────────────────────────────────────────────────

    @Test
    void spaceAdmin_rotatingSecretInItsSpace_reachesTheService() throws Exception {
        OAuthClient client = mock(OAuthClient.class);
        when(client.getClientId()).thenReturn("billing-batch");
        when(oauthClientService.rotateSecret(any(), any(), any(), any()))
                .thenReturn(new RegisteredClientResult(client, "one-time-secret"));

        mockMvc.perform(post(ROTATE)
                        .header("Authorization", bearer("space-admin"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(ROTATE_BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.clientId").value("billing-batch"));
    }

    @Test
    void otherOrgToken_registeringClient_isForbidden_andRegistersNothing() throws Exception {
        assertClientRegistrationForbidden("other-org-space-admin");
    }

    @Test
    void otherSpaceToken_registeringClient_isForbidden_andRegistersNothing() throws Exception {
        assertClientRegistrationForbidden("other-space-admin");
    }

    @Test
    void orgScopedToken_registeringClient_isForbidden_andRegistersNothing() throws Exception {
        assertClientRegistrationForbidden("org-admin");
    }

    @Test
    void otherOrgToken_rotatingSecret_isForbidden_andReturnsNoSecret() throws Exception {
        assertSecretRotationForbidden("other-org-space-admin");
    }

    @Test
    void otherSpaceToken_rotatingSecret_isForbidden_andReturnsNoSecret() throws Exception {
        assertSecretRotationForbidden("other-space-admin");
    }

    private void assertClientRegistrationForbidden(String token) throws Exception {
        mockMvc.perform(post(CLIENTS)
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CLIENT_BODY))
                .andExpect(status().isForbidden());

        verifyNoInteractions(oauthClientService);
    }

    private void assertSecretRotationForbidden(String token) throws Exception {
        mockMvc.perform(post(ROTATE)
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(ROTATE_BODY))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.oneTimePlainSecret").doesNotExist());

        verifyNoInteractions(oauthClientService);
    }

    // ── Dashboard ───────────────────────────────────────────────────────

    @Test
    void orgAdmin_readingItsDashboard_isAllowed() throws Exception {
        when(dashboardService.summarize(ORG_ID)).thenReturn(new OrganizationDashboardSummaryResult(
                ORG_ID, 3, 2, 1, 0, Instant.parse("2026-07-10T12:00:00Z")));

        mockMvc.perform(get(DASHBOARD).header("Authorization", bearer("org-admin")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.organizationId").value(ORG_ID.toString()));
    }

    @Test
    void otherOrgAdmin_readingDashboard_isForbidden_andSummarizesNothing() throws Exception {
        assertDashboardForbidden("other-org-admin");
    }

    @Test
    void spaceScopedToken_readingDashboard_isForbidden_evenWithOrgAdminRole() throws Exception {
        assertDashboardForbidden("space-scoped-org-admin");
    }

    private void assertDashboardForbidden(String token) throws Exception {
        mockMvc.perform(get(DASHBOARD).header("Authorization", bearer(token)))
                .andExpect(status().isForbidden());

        verifyNoInteractions(dashboardService);
    }
}
