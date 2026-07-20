package com.takibo.iamboot.security;

import com.takibo.securitymanagement.infrastructure.token.TokenValidatorAdapter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "management.health.mail.enabled=false")
@ActiveProfiles("test")
class ActuatorSecurityIntegrationTest {

    private static final UUID USER_ID = UUID.fromString("dddddddd-0000-0000-0000-000000000004");
    private static final UUID ORG_ID = UUID.fromString("aaaaaaaa-0000-0000-0000-000000000001");

    @Autowired
    private WebApplicationContext context;

    @MockitoBean
    private TokenValidatorAdapter tokenValidatorAdapter;

    @MockitoBean(name = "messagingHealthIndicator")
    private HealthIndicator messagingHealthIndicator;

    @MockitoBean(name = "outboxHealthIndicator")
    private HealthIndicator outboxHealthIndicator;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .apply(springSecurity())
                .build();
        when(tokenValidatorAdapter.validate("org-admin-token"))
                .thenReturn(humanClaims("R_ORG_ADMIN", true));
        when(tokenValidatorAdapter.validate("platform-admin-token"))
                .thenReturn(humanClaims("R_TAKIBO_PLATFORM_ADMIN", false));
        when(messagingHealthIndicator.health()).thenReturn(Health.up().build());
        when(outboxHealthIndicator.health()).thenReturn(Health.up().build());
    }

    @Test
    void anonymousHealth_isPublicWithoutInternalDetails() throws Exception {
        for (String path : new String[]{
                "/actuator/health",
                "/actuator/health/liveness",
                "/actuator/health/readiness"}) {
            mockMvc.perform(get(path))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").exists())
                    .andExpect(jsonPath("$.components").doesNotExist());
        }
    }

    @Test
    void diagnostics_requireAuthenticationAndPlatformAuthority() throws Exception {
        mockMvc.perform(get("/actuator/env"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/actuator/env")
                        .header("Authorization", "Bearer org-admin-token"))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/actuator/env")
                        .header("Authorization", "Bearer platform-admin-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.propertySources").isArray());
    }

    @Test
    void healthDetails_areVisibleOnlyToPlatformAuthority() throws Exception {
        mockMvc.perform(get("/actuator/health")
                        .header("Authorization", "Bearer org-admin-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.components").doesNotExist());

        mockMvc.perform(get("/actuator/health")
                        .header("Authorization", "Bearer platform-admin-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.components").exists());
    }

    @Test
    void actuatorDiscoveryAndInfo_areNotPublic() throws Exception {
        mockMvc.perform(get("/actuator"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/actuator/info"))
                .andExpect(status().isUnauthorized());
    }

    private static Map<String, Object> humanClaims(String role, boolean tenantScoped) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("sub", USER_ID.toString());
        claims.put("subjectType", "HUMAN");
        claims.put("authMethod", "PASSWORD");
        claims.put("userId", USER_ID.toString());
        claims.put("roles", List.of(role));
        if (tenantScoped) {
            claims.put("orgId", ORG_ID.toString());
            claims.put("scopeLevel", "ORGANIZATION");
        } else {
            claims.put("scopeLevel", "PLATFORM");
        }
        return claims;
    }
}
