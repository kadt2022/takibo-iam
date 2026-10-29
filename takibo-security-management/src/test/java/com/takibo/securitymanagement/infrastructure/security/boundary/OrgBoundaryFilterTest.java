package com.takibo.securitymanagement.infrastructure.security.boundary;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.takibo.identitycore.domain.exception.SpaceNotFoundException;
import com.takibo.securitymanagement.domain.exception.BoundaryCheckUnavailableException;
import com.takibo.securitymanagement.sentinel.http.SentinelHttpErrorWriter;
import com.takibo.securitymanagement.sentinel.rule.SentinelRuleHandlers;
import com.takibo.securitymanagement.sentinel.rule.SentinelRuleRegistrar;
import com.takibo.securitymanagement.sentinel.rule.SentinelRuleRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.transaction.CannotCreateTransactionException;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * SEC-TMS-06 (D12/D13) : le filtre précède ExceptionTranslationFilter et le @ControllerAdvice ;
 * il doit donc lui-même arrêter la chaîne et répondre selon le contrat d'erreur public.
 */
class OrgBoundaryFilterTest {

    private static final UUID SPACE_ID = UUID.fromString("bbbbbbbb-0000-0000-0000-000000000002");
    private static final UUID ORG_ID = UUID.fromString("aaaaaaaa-0000-0000-0000-000000000001");

    private final BoundaryMembershipService membership = mock(BoundaryMembershipService.class);
    private final ObjectMapper mapper = JsonMapper.builder().findAndAddModules().build();
    private final OrgBoundaryFilter filter = new OrgBoundaryFilter(membership, writer());

    private SentinelHttpErrorWriter writer() {
        SentinelRuleRegistry registry = new SentinelRuleRegistry(SentinelRuleHandlers.genericRule());
        SentinelRuleRegistrar.registerDefaults(registry);
        return new SentinelHttpErrorWriter(registry, mapper);
    }

    @Test
    void allFourSpacePathForms_areChecked() throws Exception {
        for (String uri : new String[]{
                "/api/spaces/" + SPACE_ID + "/users",
                "/api/v1/spaces/" + SPACE_ID + "/users",
                "/api/organizations/" + ORG_ID + "/spaces/" + SPACE_ID + "/users",
                "/api/v1/organizations/" + ORG_ID + "/spaces/" + SPACE_ID + "/users"}) {
            MockHttpServletRequest request = new MockHttpServletRequest("GET", uri);
            MockFilterChain chain = new MockFilterChain();

            filter.doFilter(request, new MockHttpServletResponse(), chain);

            assertThat(request.getAttribute("TAKIBO_BOUNDARY_OK")).as(uri).isEqualTo(Boolean.TRUE);
            assertThat(chain.getRequest()).as(uri).isNotNull();
        }

    }

    @Test
    void spaceIdParameter_isIgnored_noMembershipCheckAndNoMarker() throws Exception {
        MockHttpServletRequest byParam = new MockHttpServletRequest("GET", "/api/v1/me/spaces");
        byParam.setParameter("spaceId", SPACE_ID.toString());
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(byParam, new MockHttpServletResponse(), chain);

        verifyNoInteractions(membership);
        assertThat(byParam.getAttribute("TAKIBO_BOUNDARY_OK")).isNull();
        assertThat(chain.getRequest()).isNotNull();
    }

    @Test
    void refusal_stopsTheChain_returns403_andSetsNoBoundaryMarker() throws Exception {
        doThrow(new AccessDeniedException("ACTOR_NOT_IN_SPACE_ORG"))
                .when(membership).assertActorInSpaceOrg(eq(SPACE_ID), any());

        Result r = call("/api/v1/spaces/" + SPACE_ID + "/users");

        assertThat(r.response.getStatus()).isEqualTo(403);
        assertThat(r.body().get("code").asText()).isEqualTo("ACCESS_DENIED");
        assertThat(r.response.getContentAsString()).doesNotContain("ACTOR_NOT_IN_SPACE_ORG");
        assertThat(r.chain.getRequest()).isNull();
        assertThat(r.request.getAttribute("TAKIBO_BOUNDARY_OK")).isNull();
    }

    @Test
    void absentSpace_returns404() throws Exception {
        doThrow(new SpaceNotFoundException(SPACE_ID)).when(membership).assertActorInSpaceOrg(eq(SPACE_ID), any());

        Result r = call("/api/spaces/" + SPACE_ID + "/users");

        assertThat(r.response.getStatus()).isEqualTo(404);
        assertThat(r.body().get("code").asText()).isEqualTo("SPACE_NOT_FOUND");
        assertThat(r.chain.getRequest()).isNull();
    }

    @Test
    void unavailableDatabase_returns503_withoutInternalDetails() throws Exception {
        doThrow(new BoundaryCheckUnavailableException("jdbc:postgresql://secret-host down", new RuntimeException()))
                .when(membership).assertActorInSpaceOrg(eq(SPACE_ID), any());

        Result r = call("/api/spaces/" + SPACE_ID + "/users");

        assertThat(r.response.getStatus()).isEqualTo(503);
        assertThat(r.body().get("code").asText()).isEqualTo("SERVICE_UNAVAILABLE");
        assertThat(r.response.getContentAsString()).doesNotContain("secret-host");
        assertThat(r.body().get("traceId").asText()).isNotBlank();
        assertThat(r.chain.getRequest()).isNull();
    }

    @Test
    void outageWhileOpeningTheTransaction_isA503_notA500() throws Exception {
        // L'intercepteur @Transactional echoue avant que le service n'entre dans ses blocs JDBC :
        // l'exception arrive brute au filtre.
        for (RuntimeException outage : new RuntimeException[]{
                new CannotCreateTransactionException("Could not open JDBC Connection", new RuntimeException("down")),
                new DataAccessResourceFailureException("down"),
                new QueryTimeoutException("slow")}) {
            doThrow(outage).when(membership).assertActorInSpaceOrg(eq(SPACE_ID), any());

            Result r = call("/api/spaces/" + SPACE_ID + "/users");

            assertThat(r.response.getStatus()).as(outage.getClass().getSimpleName()).isEqualTo(503);
            assertThat(r.body().get("code").asText()).isEqualTo("SERVICE_UNAVAILABLE");
            assertThat(r.chain.getRequest()).isNull();
        }
    }

    @Test
    void anyOtherFailure_returnsGeneric500_neverAClientErrorNorTheChain() throws Exception {
        doThrow(new IllegalArgumentException("SELECT secret FROM"))
                .when(membership).assertActorInSpaceOrg(eq(SPACE_ID), any());

        Result r = call("/api/spaces/" + SPACE_ID + "/users");

        assertThat(r.response.getStatus()).isEqualTo(500);
        assertThat(r.body().get("code").asText()).isEqualTo("INTERNAL_ERROR");
        assertThat(r.response.getContentAsString()).doesNotContain("SELECT");
        assertThat(r.chain.getRequest()).isNull();
    }

    /**
     * Caractérisation (D13) : un UUID invalide dans le chemin n'active pas le filtre ; la requête
     * continue vers les gardes suivantes, qui ne reçoivent aucun Space résolu. Ce comportement
     * est documenté, pas modifié par SEC-TMS-06.
     */
    @Test
    void invalidSpaceUuidInPath_doesNotInvokeTheMembershipCheck() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/spaces/not-a-uuid/users");
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, new MockHttpServletResponse(), chain);

        verify(membership, never()).assertActorInSpaceOrg(any(), any());
        assertThat(request.getAttribute("TAKIBO_BOUNDARY_OK")).isNull();
        assertThat(chain.getRequest()).isNotNull();
    }

    private Result call(String uri) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", uri);
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();
        filter.doFilter(request, response, chain);
        return new Result(request, response, chain);
    }

    private record Result(MockHttpServletRequest request, MockHttpServletResponse response, MockFilterChain chain) {
        JsonNode body() throws Exception {
            return JsonMapper.builder().build().readTree(response.getContentAsString());
        }
    }
}
