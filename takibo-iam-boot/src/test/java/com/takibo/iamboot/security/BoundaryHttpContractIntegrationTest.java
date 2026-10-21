package com.takibo.iamboot.security;

import com.takibo.identitycore.domain.exception.SpaceNotFoundException;
import com.takibo.securitymanagement.domain.exception.BoundaryCheckUnavailableException;
import com.takibo.securitymanagement.infrastructure.security.boundary.BoundaryMembershipService;
import com.takibo.securitymanagement.infrastructure.token.TokenValidatorAdapter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * SEC-TMS-06 (D12) : contrat HTTP du contrôle d'appartenance à travers la chaîne de filtres
 * réellement configurée. Le service est doublé pour provoquer chaque issue ; la traduction est
 * celle du filtre boundary et de Sentinel, pas celle d'un test de filtre isolé.
 */
@SpringBootTest(properties = "takibo.adp.enabled=false")
@ActiveProfiles("test")
class BoundaryHttpContractIntegrationTest {

    private static final UUID ORG_ID = UUID.fromString("aaaaaaaa-0000-0000-0000-000000000001");
    private static final UUID SPACE_ID = UUID.fromString("bbbbbbbb-0000-0000-0000-000000000002");
    private static final UUID ACCOUNT_ID = UUID.fromString("cccccccc-0000-0000-0000-000000000003");
    private static final UUID USER_ID = UUID.fromString("dddddddd-0000-0000-0000-000000000004");
    private static final String URI = "/api/v1/spaces/" + SPACE_ID + "/probe";

    @Autowired
    private WebApplicationContext context;

    @MockitoBean
    private TokenValidatorAdapter tokenValidatorAdapter;

    @MockitoBean
    private BoundaryMembershipService membership;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() throws Exception {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        when(tokenValidatorAdapter.validate("space-token")).thenReturn(Map.of(
                "sub", ACCOUNT_ID.toString(),
                "subjectType", "HUMAN",
                "authMethod", "PASSWORD",
                "accountId", ACCOUNT_ID.toString(),
                "userId", USER_ID.toString(),
                "orgId", ORG_ID.toString(),
                "spaceId", SPACE_ID.toString(),
                "roles", List.of("R_SPACE_ADMIN")));
    }

    @Test
    void refusal_isA403_withTheGenericAccessDeniedCode() throws Exception {
        doThrow(new AccessDeniedException("ACTOR_NOT_IN_SPACE_ORG"))
                .when(membership).assertActorInSpaceOrg(eq(SPACE_ID), any());

        mockMvc.perform(get(URI).header("Authorization", "Bearer space-token"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }

    @Test
    void absentSpace_isA404_spaceNotFound() throws Exception {
        doThrow(new SpaceNotFoundException(SPACE_ID))
                .when(membership).assertActorInSpaceOrg(eq(SPACE_ID), any());

        mockMvc.perform(get(URI).header("Authorization", "Bearer space-token"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("SPACE_NOT_FOUND"));
    }

    @Test
    void unavailableDatabase_isA503_withoutDetails() throws Exception {
        doThrow(new BoundaryCheckUnavailableException("jdbc secret down", new RuntimeException()))
                .when(membership).assertActorInSpaceOrg(eq(SPACE_ID), any());

        mockMvc.perform(get(URI).header("Authorization", "Bearer space-token"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("SERVICE_UNAVAILABLE"))
                .andExpect(jsonPath("$.message").value("Service temporairement indisponible."));
    }

    @Test
    void structuralFailure_isAGeneric500() throws Exception {
        doThrow(new IllegalStateException("column org_id missing"))
                .when(membership).assertActorInSpaceOrg(eq(SPACE_ID), any());

        mockMvc.perform(get(URI).header("Authorization", "Bearer space-token"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"));
    }
}
