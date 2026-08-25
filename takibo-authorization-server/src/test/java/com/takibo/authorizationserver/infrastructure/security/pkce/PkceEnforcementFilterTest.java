package com.takibo.authorizationserver.infrastructure.security.pkce;

import com.takibo.authorizationserver.domain.security.tenant.TenantContext;
import com.takibo.authorizationserver.domain.security.tenant.TenantContextHolder;
import com.takibo.authorizationserver.infrastructure.jpa.entity.OAuth2ClientLookupEntity;
import com.takibo.authorizationserver.infrastructure.jpa.repository.OAuth2ClientLookupRepository;
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
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Fige le comportement de {@link PkceEnforcementFilter} (TAS-GRANTS-00).
 * <p>
 * Ce filtre est le chemin par lequel la resolution de tenant atteint aujourd'hui le flux
 * OAuth2. Deux proprietes doivent survivre au recit 01 :
 * <ul>
 *   <li><b>Le court-circuit</b> : sur {@code /oauth2/token}, tout grant autre que
 *       {@code authorization_code} traverse le filtre sans aucun acces au registre des
 *       clients. C'est ce qui protege {@code client_credentials} du resolveur de tenant
 *       factice. Le recit 01 remplace la resolution ; il ne doit pas changer ce resultat.</li>
 *   <li><b>La politique PKCE</b> : S256 obligatoire des qu'un challenge est present, et
 *       PKCE exige pour tout client PUBLIC ou marque {@code require_pkce}.</li>
 * </ul>
 * Le test {@code looks_up_client_within_the_resolved_tenant} documente volontairement le
 * couplage actuel au tenant resolu : c'est precisement ce que le recit 01 remplacera par
 * une resolution globale sur {@code client_id}.
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

    @Mock private OAuth2ClientLookupRepository repository;
    @Mock private OAuth2HttpErrorWriter errorWriter;
    @Mock private FilterChain chain;

    private final ClientIdExtractor clientIdExtractor = new ClientIdExtractor();

    private PkceEnforcementFilter filter() {
        return new PkceEnforcementFilter(repository, clientIdExtractor, errorWriter);
    }

    @AfterEach
    void clearTenantContext() {
        TenantContextHolder.clear();
    }

    // ---------- Court-circuit : la garde de non-regression client_credentials ----------

    @Test
    void given_client_credentials_token_request_when_filter_then_never_touches_the_client_registry()
            throws Exception {
        MockHttpServletRequest request = tokenRequest("client_credentials");
        request.addHeader("Authorization", basic(CLIENT_ID));
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter().doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
        verifyNoInteractions(repository);
        verifyNoInteractions(errorWriter);
    }

    @Test
    void given_client_credentials_token_request_without_any_tenant_context_when_filter_then_still_passes()
            throws Exception {
        // Aucun TenantContext pose : le court-circuit intervient avant la lecture du contexte.
        MockHttpServletRequest request = tokenRequest("client_credentials");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter().doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
        verifyNoInteractions(repository);
    }

    @Test
    void given_refresh_token_grant_when_filter_then_also_bypasses_pkce_enforcement() throws Exception {
        // Le court-circuit couvre tout grant non authorization_code : il protegera de la
        // meme facon le refresh_token introduit au recit 05.
        MockHttpServletRequest request = tokenRequest("refresh_token");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter().doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
        verifyNoInteractions(repository);
    }

    @Test
    void given_non_oauth2_uri_when_filter_then_passes_through_untouched() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/organizations");
        request.setRequestURI("/api/v1/organizations");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter().doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
        verifyNoInteractions(repository);
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
    void given_missing_tenant_context_when_filter_then_invalid_request() throws Exception {
        MockHttpServletRequest request = authorizeRequest();
        request.setParameter(PARAM_CLIENT_ID, CLIENT_ID);
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter().doFilter(request, response, chain);

        verify(errorWriter).writeInvalidRequest(
                eq("Tenant context not set"), eq(request), eq(response));
        verify(chain, never()).doFilter(any(), any());
    }

    @Test
    void given_unknown_client_when_filter_then_invalid_client() throws Exception {
        givenTenantContext();
        when(repository.findByOrgIdAndSpaceIdAndClientId(ORG_ID, SPACE_ID, CLIENT_ID))
                .thenReturn(Optional.empty());

        MockHttpServletRequest request = authorizeRequest();
        request.setParameter(PARAM_CLIENT_ID, CLIENT_ID);
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter().doFilter(request, response, chain);

        verify(errorWriter).writeInvalidClient(eq("Client not found"), eq(request), eq(response));
        verify(chain, never()).doFilter(any(), any());
    }

    @Test
    void given_authorize_when_filter_then_looks_up_client_within_the_resolved_tenant() throws Exception {
        // Couplage actuel documente : la recherche est bornee par le tenant resolu.
        // Le recit 01 la remplacera par une resolution globale sur client_id.
        givenTenantContext();
        givenClient(false, OAuth2ClientLookupEntity.ClientType.CONFIDENTIAL);

        MockHttpServletRequest request = authorizeRequest();
        request.setParameter(PARAM_CLIENT_ID, CLIENT_ID);

        filter().doFilter(request, new MockHttpServletResponse(), chain);

        verify(repository).findByOrgIdAndSpaceIdAndClientId(ORG_ID, SPACE_ID, CLIENT_ID);
    }

    // ---------- Politique PKCE sur /oauth2/authorize ----------

    @Test
    void given_public_client_without_challenge_when_authorize_then_invalid_request() throws Exception {
        // Un client PUBLIC exige PKCE meme si require_pkce est faux en base.
        givenTenantContext();
        givenClient(false, OAuth2ClientLookupEntity.ClientType.PUBLIC);

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
        givenTenantContext();
        givenClient(true, OAuth2ClientLookupEntity.ClientType.CONFIDENTIAL);

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
        givenTenantContext();
        givenClient(true, OAuth2ClientLookupEntity.ClientType.PUBLIC);

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
        givenTenantContext();
        givenClient(false, OAuth2ClientLookupEntity.ClientType.CONFIDENTIAL);

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
        givenTenantContext();
        givenClient(true, OAuth2ClientLookupEntity.ClientType.PUBLIC);

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
        givenTenantContext();
        givenClient(false, OAuth2ClientLookupEntity.ClientType.CONFIDENTIAL);

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
        givenTenantContext();
        givenClient(true, OAuth2ClientLookupEntity.ClientType.PUBLIC);

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
        givenTenantContext();
        givenClient(true, OAuth2ClientLookupEntity.ClientType.PUBLIC);

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
        givenTenantContext();
        givenClient(false, OAuth2ClientLookupEntity.ClientType.CONFIDENTIAL);

        MockHttpServletRequest request = tokenRequest(GRANT_AUTHORIZATION_CODE);
        request.setParameter(PARAM_CLIENT_ID, CLIENT_ID);
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter().doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
        verifyNoInteractions(errorWriter);
    }

    // ---------- Fixtures ----------

    private void givenTenantContext() {
        TenantContextHolder.set(new TenantContext(ORG_ID, SPACE_ID));
    }

    private void givenClient(boolean requirePkce, OAuth2ClientLookupEntity.ClientType clientType) {
        OAuth2ClientLookupEntity entity = new OAuth2ClientLookupEntity();
        ReflectionTestUtils.setField(entity, "requirePkce", requirePkce);
        ReflectionTestUtils.setField(entity, "clientType", clientType);
        when(repository.findByOrgIdAndSpaceIdAndClientId(ORG_ID, SPACE_ID, CLIENT_ID))
                .thenReturn(Optional.of(entity));
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
