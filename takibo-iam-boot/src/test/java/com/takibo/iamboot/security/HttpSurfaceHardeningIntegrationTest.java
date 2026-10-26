package com.takibo.iamboot.security;

import com.takibo.securitymanagement.infrastructure.token.TokenValidatorAdapter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Surface HTTP fermée par défaut, à travers la vraie chaîne Spring Security : les anciens préfixes
 * publics sans contrôleur ne le sont plus, toute route non déclarée est refusée même à un appelant
 * authentifié, et la documentation OpenAPI est fermée hors du profil dev.
 */
@SpringBootTest(properties = "takibo.adp.enabled=false")
@ActiveProfiles("test")
class HttpSurfaceHardeningIntegrationTest {

    private static final UUID ORG_ID = UUID.fromString("aaaaaaaa-0000-0000-0000-000000000001");
    private static final UUID SPACE_ID = UUID.fromString("bbbbbbbb-0000-0000-0000-000000000002");
    private static final UUID USER_ID = UUID.fromString("dddddddd-0000-0000-0000-000000000004");

    @Autowired
    private WebApplicationContext context;

    @MockitoBean
    private TokenValidatorAdapter tokenValidatorAdapter;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() throws Exception {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        when(tokenValidatorAdapter.validate("org-admin")).thenReturn(Map.of(
                "sub", USER_ID.toString(),
                "subjectType", "HUMAN",
                "authMethod", "PASSWORD",
                "userId", USER_ID.toString(),
                "orgId", ORG_ID.toString(),
                "spaceId", SPACE_ID.toString(),
                "roles", List.of("R_ORG_ADMIN")));
    }

    @Test
    void formerPublicPrefixes_withoutController_areNoLongerPublic() throws Exception {
        for (String path : List.of("/api/public/anything", "/api/auth/anything")) {
            mockMvc.perform(get(path)).andExpect(status().isUnauthorized());
            mockMvc.perform(post(path)).andExpect(status().isUnauthorized());
        }
    }

    @Test
    void undeclaredRoutes_areDenied_evenToAnAuthenticatedCaller() throws Exception {
        for (String path : List.of("/api/public/anything", "/api/users/" + USER_ID, "/api/organizations/" + ORG_ID,
                "/api/roles", "/api/secrets", "/api/permissions", "/api/platform/tenants", "/not-a-route")) {
            mockMvc.perform(get(path).header("Authorization", "Bearer org-admin"))
                    .andExpect(status().isForbidden());
        }
    }

    @Test
    void openApiDocumentation_isClosed_outsideTheDevProfile() throws Exception {
        for (String path : List.of("/v3/api-docs", "/swagger-ui/index.html", "/swagger-ui.html")) {
            mockMvc.perform(get(path)).andExpect(status().isUnauthorized());
            mockMvc.perform(get(path).header("Authorization", "Bearer org-admin")).andExpect(status().isForbidden());
        }
    }
}
