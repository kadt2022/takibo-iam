package com.takibo.authorizationserver.infrastructure.security.pkce;

import com.takibo.authorizationserver.domain.client.ClientPlan;
import com.takibo.authorizationserver.domain.client.ClientType;
import com.takibo.authorizationserver.domain.client.ResolvedOAuthClient;
import com.takibo.authorizationserver.domain.client.ResolvedOAuthClientContextHolder;
import com.takibo.authorizationserver.infrastructure.security.error.OAuth2HttpErrorWriter;
import com.takibo.authorizationserver.infrastructure.security.tenant.ClientIdExtractor;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Set;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * Fige le comportement de {@link PkceEnforcementFilter} (TAS-GRANTS-01).
 * <p>
 * Deux propriétés portantes :
 * <ul>
 *   <li><b>Le court-circuit</b> : sur {@code /oauth2/token}, tout grant autre que
 *       {@code authorization_code} traverse le filtre sans toucher au client résolu. C'est ce
 *       qui protège {@code client_credentials} d'une résolution qui aurait échoué.</li>
 *   <li><b>La politique PKCE</b> : S256 obligatoire dès qu'un challenge est présent, et PKCE
 *       exigé pour tout client {@code PUBLIC} ou marqué {@code require_pkce} — désormais lu
 *       directement sur {@link ResolvedOAuthClient#pkceRequired()}, sans second lookup par
 *       {@code (org_id, space_id, client_id)} : le filtre n'interroge plus aucun dépôt, il lit
 *       le client déjà résolu par {@code TenantResolutionFilter}.</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class PkceEnforcementFilterTest {

    private static final String CLIENT_ID = "busa-finance";
    private static final UUID ORG_ID = UUID.fromString("674b889c-4d4e-47bd-bdf6-972dc84f1b49");
    private static final UUID SPACE_ID = UUID.fromString("8932f9bc-0af0-4c64-94c8-abb0150c348b");
    private static final String PARAM_CLIENT_ID = "client_id";
    private static final String GRANT_AUTHORIZATION_CODE = "authorization_code";
    private static final String PARAM_CHALLENGE_METHOD = "code_challenge_method";
    private static final String ERROR_CHALLENGE_REQUIRED = "code_challenge required";
    private static final String CHALLENGE = "E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM";

    @Mock private OAuth2HttpErrorWriter errorWriter;
    @Mock private FilterChain chain;

    private final ClientIdExtractor clientIdExtractor = new ClientIdExtractor();

    private PkceEnforcementFilter filter() {
        return new PkceEnforcementFilter(clientIdExtractor, errorWriter);
    }

    @AfterEach
    void clearResolvedClientContext() {
        ResolvedOAuthClientContextHolder.clear();
    }

    // ---------- Court-circuit : la garde de non-regression client_credentials ----------

    @Test
    void given_client_credentials_token_request_when_filter_then_never_touches_the_resolved_client()
            throws Exception {
        MockHttpServletRequest request = tokenRequest("client_credentials");
        request.addHeader("Authorization", basic(CLIENT_ID));
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter().doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
        verifyNoInteractions(errorWriter);
    }

    @Test
    void given_client_credentials_token_request_without_any_resolved_client_when_filter_then_still_passes()
            throws Exception {
        // Aucun ResolvedOAuthClient pose : le court-circuit intervient avant sa lecture.
        MockHttpServletRequest request = tokenRequest("client_credentials");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter().doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
    }

    @Test
    void given_refresh_token_grant_when_filter_then_also_bypasses_pkce_enforcement() throws Exception {
        // Le court-circuit couvre tout grant non authorization_code : il protegera de la
        // meme facon le refresh_token introduit au recit 05.
        MockHttpServletRequest request = tokenRequest("refresh_token");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter().doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
    }

    @Test
    void given_non_oauth2_uri_when_filter_then_passes_through_untouched() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/organizations");
        request.setRequestURI("/api/v1/organizations");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter().doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
    }

    // ---------- Identification du client ----------

    @Test
    void given_authorize_without_client_id_when_filter_then_invalid_request() throws Exception {
        MockHttpServletRequest request = authorizeRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter().doFilter(request, response, chain);

        verify(errorWriter).writeInvalidRequest(
                eq("Missing required parameter: client_id"), eq(request), eq(response));
        verify(chain, never()).doFilter(any(), any());
    }

    @Test
    void given_authorization_code_token_without_client_id_when_filter_then_invalid_client()
            throws Exception {
        MockHttpServletRequest request = tokenRequest(GRANT_AUTHORIZATION_CODE);
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter().doFilter(request, response, chain);

        verify(errorWriter).writeInvalidClient(
                eq("Client identification required"), eq(request), eq(response));
        verify(chain, never()).doFilter(any(), any());
    }

    @Test
    void given_no_client_resolved_when_filter_then_invalid_request() throws Exception {
        // Ni TenantResolutionFilter n'a resolu de client (client inconnu, ou desactivation
        // future), ni ce filtre ne relit quoi que ce soit lui-meme : l'absence dans le
        // holder est le seul signal, qu'elle vienne d'un client inconnu ou d'un contexte non
        // encore peuple.
        MockHttpServletRequest request = authorizeRequest();
        request.setParameter(PARAM_CLIENT_ID, CLIENT_ID);
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter().doFilter(request, response, chain);

        verify(errorWriter).writeInvalidRequest(
                eq("Tenant context not set"), eq(request), eq(response));
        verify(chain, never()).doFilter(any(), any());
    }

    // ---------- Politique PKCE sur /oauth2/authorize ----------

    @Test
    void given_public_client_without_challenge_when_authorize_then_invalid_request() throws Exception {
        // Un client PUBLIC exige PKCE meme si require_pkce est faux en base.
        givenResolvedClient(false, ClientType.PUBLIC);

        MockHttpServletRequest request = authorizeRequest();
        request.setParameter(PARAM_CLIENT_ID, CLIENT_ID);
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter().doFilter(request, response, chain);

        verify(errorWriter).writeInvalidRequest(
                eq(ERROR_CHALLENGE_REQUIRED), eq(request), eq(response));
        verify(chain, never()).doFilter(any(), any());
    }

    @Test
    void given_client_requiring_pkce_without_challenge_when_authorize_then_invalid_request()
            throws Exception {
        givenResolvedClient(true, ClientType.CONFIDENTIAL);

        MockHttpServletRequest request = authorizeRequest();
        request.setParameter(PARAM_CLIENT_ID, CLIENT_ID);
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter().doFilter(request, response, chain);

        verify(errorWriter).writeInvalidRequest(
                eq(ERROR_CHALLENGE_REQUIRED), eq(request), eq(response));
        verify(chain, never()).doFilter(any(), any());
    }

    @Test
    void given_challenge_with_plain_method_when_authorize_then_rejects_non_s256() throws Exception {
        givenResolvedClient(true, ClientType.PUBLIC);

        MockHttpServletRequest request = authorizeRequest();
        request.setParameter(PARAM_CLIENT_ID, CLIENT_ID);
        request.setParameter("code_challenge", CHALLENGE);
        request.setParameter(PARAM_CHALLENGE_METHOD, "plain");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter().doFilter(request, response, chain);

        verify(errorWriter).writeInvalidRequest(
                eq("code_challenge_method must be S256"), eq(request), eq(response));
        verify(chain, never()).doFilter(any(), any());
    }

    @Test
    void given_challenge_method_without_challenge_when_authorize_then_invalid_request()
            throws Exception {
        givenResolvedClient(false, ClientType.CONFIDENTIAL);

        MockHttpServletRequest request = authorizeRequest();
        request.setParameter(PARAM_CLIENT_ID, CLIENT_ID);
        request.setParameter(PARAM_CHALLENGE_METHOD, "S256");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter().doFilter(request, response, chain);

        verify(errorWriter).writeInvalidRequest(
                eq(ERROR_CHALLENGE_REQUIRED), eq(request), eq(response));
        verify(chain, never()).doFilter(any(), any());
    }

    @Test
    void given_valid_s256_challenge_when_authorize_then_passes() throws Exception {
        givenResolvedClient(true, ClientType.PUBLIC);

        MockHttpServletRequest request = authorizeRequest();
        request.setParameter(PARAM_CLIENT_ID, CLIENT_ID);
        request.setParameter("code_challenge", CHALLENGE);
        request.setParameter(PARAM_CHALLENGE_METHOD, "S256");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter().doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
        verifyNoInteractions(errorWriter);
    }

    @Test
    void given_confidential_client_not_requiring_pkce_when_authorize_then_passes_without_challenge()
            throws Exception {
        givenResolvedClient(false, ClientType.CONFIDENTIAL);

        MockHttpServletRequest request = authorizeRequest();
        request.setParameter(PARAM_CLIENT_ID, CLIENT_ID);
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter().doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
        verifyNoInteractions(errorWriter);
    }

    // ---------- Politique PKCE sur /oauth2/token en authorization_code ----------

    @Test
    void given_authorization_code_token_without_verifier_when_pkce_required_then_invalid_request()
            throws Exception {
        givenResolvedClient(true, ClientType.PUBLIC);

        MockHttpServletRequest request = tokenRequest(GRANT_AUTHORIZATION_CODE);
        request.setParameter(PARAM_CLIENT_ID, CLIENT_ID);
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter().doFilter(request, response, chain);

        verify(errorWriter).writeInvalidRequest(
                eq("code_verifier required"), eq(request), eq(response));
        verify(chain, never()).doFilter(any(), any());
    }

    @Test
    void given_authorization_code_token_with_verifier_when_pkce_required_then_passes()
            throws Exception {
        givenResolvedClient(true, ClientType.PUBLIC);

        MockHttpServletRequest request = tokenRequest(GRANT_AUTHORIZATION_CODE);
        request.setParameter(PARAM_CLIENT_ID, CLIENT_ID);
        request.setParameter("code_verifier", "dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter().doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
        verifyNoInteractions(errorWriter);
    }

    @Test
    void given_authorization_code_token_without_verifier_when_pkce_not_required_then_passes()
            throws Exception {
        givenResolvedClient(false, ClientType.CONFIDENTIAL);

        MockHttpServletRequest request = tokenRequest(GRANT_AUTHORIZATION_CODE);
        request.setParameter(PARAM_CLIENT_ID, CLIENT_ID);
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter().doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
        verifyNoInteractions(errorWriter);
    }

    @Test
    void given_a_platform_client_when_authorize_then_pkce_policy_still_applies_without_any_tenant()
            throws Exception {
        // Un client PLATFORM n'a ni organisation ni space, mais sa politique PKCE reste
        // applicable : elle ne depend jamais de ce que la requete pretend connaitre du tenant.
        ResolvedOAuthClientContextHolder.set(new ResolvedOAuthClient(
                "platform-id", "postman-client", ClientPlan.PLATFORM, null, null,
                ClientType.PUBLIC, false, false, false, null,
                "none", null, null, null, null, null, null,
                Set.of("api.read"), Set.of("authorization_code"), Set.of("https://cb"), Set.of()));

        MockHttpServletRequest request = authorizeRequest();
        request.setParameter(PARAM_CLIENT_ID, "postman-client");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter().doFilter(request, response, chain);

        verify(errorWriter).writeInvalidRequest(
                eq(ERROR_CHALLENGE_REQUIRED), eq(request), eq(response));
    }

    // ---------- Fixtures ----------

    private void givenResolvedClient(boolean requirePkce, ClientType clientType) {
        boolean requireClientSecret = clientType == ClientType.CONFIDENTIAL;
        ResolvedOAuthClientContextHolder.set(new ResolvedOAuthClient(
                "registered-" + CLIENT_ID, CLIENT_ID, ClientPlan.SPACE, ORG_ID, SPACE_ID,
                clientType, requirePkce, false, requireClientSecret,
                requireClientSecret ? "hash" : null,
                requireClientSecret ? "client_secret_basic" : "none",
                null, null, null, null, null, null,
                Set.of("api.read"), Set.of("authorization_code"), Set.of("https://cb"), Set.of()));
    }

    private static MockHttpServletRequest authorizeRequest() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/oauth2/authorize");
        request.setRequestURI("/oauth2/authorize");
        return request;
    }

    private static MockHttpServletRequest tokenRequest(String grantType) {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/oauth2/token");
        request.setRequestURI("/oauth2/token");
        request.setParameter("grant_type", grantType);
        return request;
    }

    private static String basic(String clientId) {
        String raw = clientId + ":secret";
        return "Basic " + Base64.getEncoder()
                .encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }
}
