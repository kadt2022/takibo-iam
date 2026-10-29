package com.takibo.iamboot.security;

import com.takibo.identitycore.application.identity.service.UserRegistrationOrchestrator;
import com.takibo.identitycore.domain.exception.SpaceNotFoundException;
import com.takibo.identitycore.integration.space.port.ResolvedSpaceKey;
import com.takibo.identitycore.integration.space.port.SpaceKeyResolutionCase;
import com.takibo.managementservice.application.query.port.SpaceQueryCase;
import com.takibo.securitymanagement.infrastructure.security.boundary.BoundaryMembershipService;
import com.takibo.securitymanagement.infrastructure.token.TokenValidatorAdapter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
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
 * Réponses observables aux frontières, à travers la VRAIE chaîne Spring Security :
 * <ul>
 *   <li>un paramètre {@code spaceId} ne déclenche plus de contrôle d'appartenance : la réponse
 *       ne dépend plus de l'existence du space désigné ;</li>
 *   <li>un token PLATFORM sans organisation n'entre dans aucune organisation, ni par une route
 *       UUID (contrôle central RBAC-06), ni par une route lisible (gardes applicatives) :
 *       l'intervention plateforme reste à situer (RBAC-08).</li>
 * </ul>
 * Seuls la validation du token et les accès aux données sont doublés.
 */
@SpringBootTest(properties = "takibo.adp.enabled=false")
@ActiveProfiles("test")
class BoundaryObservabilityHttpIntegrationTest {

    private static final UUID ORG_ID = UUID.fromString("aaaaaaaa-0000-0000-0000-000000000001");
    private static final UUID SPACE_ID = UUID.fromString("bbbbbbbb-0000-0000-0000-000000000002");
    private static final UUID EXISTING_FOREIGN_SPACE = UUID.fromString("cccccccc-0000-0000-0000-000000000003");
    private static final UUID MISSING_SPACE = UUID.fromString("dddddddd-0000-0000-0000-000000000004");
    private static final UUID USER_ID = UUID.fromString("eeeeeeee-0000-0000-0000-000000000005");

    private static final String BODY = """
            {"email":"plateforme@takibo.io","rawPassword":"An0ther!Passw0rd",
             "username":"plateforme","firstName":"Plate","lastName":"Forme"}""";

    @Autowired
    private WebApplicationContext context;

    @MockitoBean
    private TokenValidatorAdapter tokenValidatorAdapter;

    @MockitoBean
    private BoundaryMembershipService boundaryMembershipService;

    @MockitoBean
    private SpaceQueryCase spaceQueryCase;

    @MockitoBean
    private SpaceKeyResolutionCase spaceKeyResolution;

    @MockitoBean
    private UserRegistrationOrchestrator registrationOrchestrator;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() throws Exception {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();

        when(tokenValidatorAdapter.validate("platform")).thenReturn(platformAdminClaims());
        when(spaceKeyResolution.resolve("acme", "finance"))
                .thenReturn(new ResolvedSpaceKey(ORG_ID, SPACE_ID, "acme", "finance"));

        // Ce que répondrait le contrôle d'appartenance s'il était (encore) déclenché :
        // refus pour un space existant d'une autre organisation, absence pour un space inconnu.
        doThrow(new AccessDeniedException("ACTOR_NOT_IN_SPACE_ORG"))
                .when(boundaryMembershipService).assertActorInSpaceOrg(eq(EXISTING_FOREIGN_SPACE), any());
        doThrow(new SpaceNotFoundException(MISSING_SPACE))
                .when(boundaryMembershipService).assertActorInSpaceOrg(eq(MISSING_SPACE), any());
    }

    private static Map<String, Object> platformAdminClaims() {
        Map<String, Object> claims = new HashMap<>();
        claims.put("sub", USER_ID.toString());
        claims.put("subjectType", "HUMAN");
        claims.put("authMethod", "PASSWORD");
        claims.put("userId", USER_ID.toString());
        claims.put("roles", List.of("R_TAKIBO_PLATFORM_ADMIN"));
        claims.put("scopeLevel", "PLATFORM");
        return claims;
    }

    // ── Paramètre spaceId : plus d'oracle d'existence ────────────────

    @Test
    void spaceIdParameter_givesTheSameAnswer_whetherTheSpaceExistsOrNot() throws Exception {
        for (UUID spaceId : new UUID[]{EXISTING_FOREIGN_SPACE, MISSING_SPACE}) {
            mockMvc.perform(get("/api/v1/me/spaces").param("spaceId", spaceId.toString())
                            .header("Authorization", "Bearer platform"))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
        }

        verifyNoInteractions(boundaryMembershipService);
    }

    // ── Token PLATFORM sans organisation (RBAC-08 non situé) ─────────

    @Test
    void platformToken_listingAnOrganizationsSpaces_isForbidden() throws Exception {
        mockMvc.perform(get("/api/v1/orgs/" + ORG_ID + "/spaces").header("Authorization", "Bearer platform"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));

        verifyNoInteractions(spaceQueryCase);
    }

    @Test
    void platformToken_readingASpace_isForbidden() throws Exception {
        mockMvc.perform(get("/api/v1/orgs/" + ORG_ID + "/spaces/" + SPACE_ID)
                        .header("Authorization", "Bearer platform"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));

        verifyNoInteractions(spaceQueryCase);
    }

    @Test
    void platformToken_creatingAUserThroughTheReadableRoute_isForbidden_andRegistersNothing() throws Exception {
        mockMvc.perform(post("/api/v1/orgs/acme/spaces/finance/users")
                        .header("Authorization", "Bearer platform")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BODY))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));

        verify(registrationOrchestrator, never()).registerUser(any());
    }
}
