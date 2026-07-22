package com.takibo.iamboot.security;

import com.takibo.managementservice.application.query.port.SpaceQueryCase;
import com.takibo.managementservice.application.query.result.SpaceDetailsResult;
import com.takibo.managementservice.application.query.result.SpacePageResult;
import com.takibo.managementservice.domain.model.SpaceStatus;
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

import java.net.URI;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Preuve d'intégration des gardes de la surface spaces TMS : chaque requête traverse
 * la VRAIE chaîne Spring Security (JwtAuthenticationFilter -> PolicyBasedAuthorizationManager
 * -> PolicyEvaluator) jusqu'au SpaceController. Seules la validation cryptographique du
 * token (TokenValidatorAdapter) et la lecture JPA (SpaceQueryCase) sont doublées —
 * la politique d'autorisation déterministe, elle, est réelle. Le risque adaptatif est
 * neutralisé afin que l'heure du runner CI ne transforme pas un ALLOW en CHALLENGE.
 * La saga BVT post-merge complète cette preuve mais ne la remplace pas.
 */
@SpringBootTest(properties = "takibo.adp.enabled=false")
@ActiveProfiles("test")
class SpaceSurfaceSecurityIntegrationTest {

    private static final UUID ORG_ID = UUID.fromString("aaaaaaaa-0000-0000-0000-000000000001");
    private static final UUID OTHER_ORG_ID = UUID.fromString("99999999-0000-0000-0000-000000000009");
    private static final UUID SPACE_ID = UUID.fromString("bbbbbbbb-0000-0000-0000-000000000002");
    private static final UUID OTHER_SPACE_ID = UUID.fromString("cccccccc-0000-0000-0000-000000000003");
    private static final UUID USER_ID = UUID.fromString("dddddddd-0000-0000-0000-000000000004");
    private static final String BASE = "/api/v1/orgs/" + ORG_ID + "/spaces";

    @Autowired
    private WebApplicationContext context;

    @MockitoBean
    private TokenValidatorAdapter tokenValidatorAdapter;

    @MockitoBean
    private SpaceQueryCase spaceQueryCase;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() throws Exception {
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .apply(springSecurity())
                .build();
        when(tokenValidatorAdapter.validate("org-admin-token"))
                .thenReturn(humanClaims(ORG_ID, SPACE_ID, "R_ORG_ADMIN"));
        when(tokenValidatorAdapter.validate("space-admin-token"))
                .thenReturn(humanClaims(ORG_ID, SPACE_ID, "R_SPACE_ADMIN"));
        when(tokenValidatorAdapter.validate("cross-org-token"))
                .thenReturn(humanClaims(OTHER_ORG_ID, SPACE_ID, "R_ORG_ADMIN"));
    }

    private static Map<String, Object> humanClaims(UUID orgId, UUID spaceId, String... roles) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("sub", USER_ID.toString());
        claims.put("subjectType", "HUMAN");
        claims.put("authMethod", "PASSWORD");
        claims.put("userId", USER_ID.toString());
        claims.put("orgId", orgId.toString());
        claims.put("spaceId", spaceId.toString());
        claims.put("roles", List.of(roles));
        return claims;
    }

    @Test
    void spaceAdmin_creatingSpace_isForbidden() throws Exception {
        mockMvc.perform(post(BASE)
                        .header("Authorization", "Bearer space-admin-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Busa\",\"code\":\"busa\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void encodedOrParameterizedOrgUuid_cannotBypassSpaceListPolicy() throws Exception {
        when(spaceQueryCase.listSpaces(eq(ORG_ID), any(), any(), anyInt(), anyInt(), any()))
                .thenReturn(new SpacePageResult(List.of(), 0, 20, 0, 0));
        String encodedOrg = ORG_ID.toString().replace("-", "%2D");

        mockMvc.perform(get(URI.create("/api/v1/orgs/" + encodedOrg + "/spaces"))
                        .header("Authorization", "Bearer space-admin-token"))
                .andExpect(status().isForbidden());

        // StrictHttpFirewall rejects raw matrix parameters before authorization.
        mockMvc.perform(get(URI.create("/api/v1/orgs/" + ORG_ID + ";source=review/spaces"))
                        .header("Authorization", "Bearer space-admin-token"))
                .andExpect(status().isBadRequest());

        verify(spaceQueryCase, never()).listSpaces(any(), any(), any(), anyInt(), anyInt(), any());
    }

    @Test
    void orgAdmin_listingSpaces_isAllowed() throws Exception {
        when(spaceQueryCase.listSpaces(eq(ORG_ID), any(), any(), anyInt(), anyInt(), any()))
                .thenReturn(new SpacePageResult(List.of(), 0, 20, 0, 0));

        mockMvc.perform(get(BASE)
                        .header("Authorization", "Bearer org-admin-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isEmpty());
    }

    @Test
    void crossOrgToken_listingSpaces_isForbidden() throws Exception {
        mockMvc.perform(get(BASE)
                        .header("Authorization", "Bearer cross-org-token"))
                .andExpect(status().isForbidden());
    }

    @Test
    void spaceAdmin_readingItsOwnSpace_isAllowed() throws Exception {
        when(spaceQueryCase.getSpace(ORG_ID, SPACE_ID)).thenReturn(new SpaceDetailsResult(
                SPACE_ID, ORG_ID, "busa", "Busa", null,
                SpaceStatus.ACTIVE, null, Instant.parse("2026-07-10T12:00:00Z"),
                USER_ID, Instant.parse("2026-07-10T12:00:00Z"),
                Instant.parse("2026-07-10T12:00:00Z"), 0L));

        mockMvc.perform(get(BASE + "/" + SPACE_ID)
                        .header("Authorization", "Bearer space-admin-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(SPACE_ID.toString()));
    }

    @Test
    void spaceAdmin_readingAnotherSpace_isForbidden() throws Exception {
        mockMvc.perform(get(BASE + "/" + OTHER_SPACE_ID)
                        .header("Authorization", "Bearer space-admin-token"))
                .andExpect(status().isForbidden());
    }
}
