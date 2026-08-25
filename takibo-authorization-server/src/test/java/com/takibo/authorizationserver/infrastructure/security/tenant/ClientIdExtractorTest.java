package com.takibo.authorizationserver.infrastructure.security.tenant;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Fige le contrat d'extraction du {@code client_id} (TAS-GRANTS-00).
 * <p>
 * Ce composant decide quel client sera resolu par {@code TenantResolutionFilter} et
 * {@code PkceEnforcementFilter}. Son comportement actuel est la reference : le recit 01
 * remplacera la resolution en aval, pas la facon dont l'identifiant est lu dans la requete.
 * <p>
 * Point charniere : la priorite s'inverse entre les deux surfaces. Sur le token endpoint,
 * l'en-tete Basic prime sur le parametre ; par defaut, c'est l'inverse. Cette asymetrie est
 * portante et doit rester explicite.
 */
class ClientIdExtractorTest {

    private static final String CLIENT_ID = "busa-finance";
    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String PARAM_CLIENT_ID = "client_id";
    private static final String BASIC_PREFIX = "Basic ";
    private static final String SECRET = "secret";
    private static final String FROM_PARAMETER = "from-parameter";
    private static final String FROM_BASIC = "from-basic";

    private final ClientIdExtractor extractor = new ClientIdExtractor();

    // ---------- /oauth2/authorize : parametre uniquement ----------

    @Test
    void given_client_id_parameter_when_extract_for_authorize_then_returns_it() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setParameter(PARAM_CLIENT_ID, CLIENT_ID);

        assertThat(extractor.extractForAuthorize(request)).isEqualTo(CLIENT_ID);
    }

    @Test
    void given_no_client_id_when_extract_for_authorize_then_returns_null() {
        assertThat(extractor.extractForAuthorize(new MockHttpServletRequest())).isNull();
    }

    @Test
    void given_blank_client_id_parameter_when_extract_for_authorize_then_returns_null() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setParameter(PARAM_CLIENT_ID, "   ");

        assertThat(extractor.extractForAuthorize(request)).isNull();
    }

    @Test
    void given_basic_auth_only_when_extract_for_authorize_then_ignores_header() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(AUTHORIZATION_HEADER, basic(CLIENT_ID, SECRET));

        assertThat(extractor.extractForAuthorize(request)).isNull();
    }

    // ---------- /oauth2/token : Basic prioritaire, repli sur le parametre ----------

    @Test
    void given_basic_auth_and_parameter_when_extract_for_token_then_basic_auth_wins() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(AUTHORIZATION_HEADER, basic(FROM_BASIC, SECRET));
        request.setParameter(PARAM_CLIENT_ID, FROM_PARAMETER);

        assertThat(extractor.extractForToken(request)).isEqualTo(FROM_BASIC);
    }

    @Test
    void given_parameter_only_when_extract_for_token_then_falls_back_to_parameter() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setParameter(PARAM_CLIENT_ID, CLIENT_ID);

        assertThat(extractor.extractForToken(request)).isEqualTo(CLIENT_ID);
    }

    @Test
    void given_neither_basic_auth_nor_parameter_when_extract_for_token_then_returns_null() {
        assertThat(extractor.extractForToken(new MockHttpServletRequest())).isNull();
    }

    // ---------- extractDefault : parametre prioritaire, repli sur Basic ----------

    @Test
    void given_basic_auth_and_parameter_when_extract_default_then_parameter_wins() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(AUTHORIZATION_HEADER, basic(FROM_BASIC, SECRET));
        request.setParameter(PARAM_CLIENT_ID, FROM_PARAMETER);

        assertThat(extractor.extractDefault(request)).isEqualTo(FROM_PARAMETER);
    }

    @Test
    void given_basic_auth_only_when_extract_default_then_falls_back_to_header() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(AUTHORIZATION_HEADER, basic(CLIENT_ID, SECRET));

        assertThat(extractor.extractDefault(request)).isEqualTo(CLIENT_ID);
    }

    @Test
    void given_nothing_when_extract_default_then_returns_null() {
        assertThat(extractor.extractDefault(new MockHttpServletRequest())).isNull();
    }

    // ---------- Indices de decouverte et userinfo : parametre uniquement ----------

    @Test
    void given_client_id_parameter_when_extract_hints_then_returns_it() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setParameter(PARAM_CLIENT_ID, CLIENT_ID);

        assertThat(extractor.extractForDiscoveryHint(request)).isEqualTo(CLIENT_ID);
        assertThat(extractor.extractForUserInfoHint(request)).isEqualTo(CLIENT_ID);
    }

    @Test
    void given_no_parameter_when_extract_hints_then_returns_null() {
        MockHttpServletRequest request = new MockHttpServletRequest();

        assertThat(extractor.extractForDiscoveryHint(request)).isNull();
        assertThat(extractor.extractForUserInfoHint(request)).isNull();
    }

    // ---------- En-tete Basic malforme : jamais d'exception, toujours null ----------

    @Test
    void given_non_basic_scheme_when_extract_for_token_then_returns_null() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(AUTHORIZATION_HEADER, "Bearer some-token");

        assertThat(extractor.extractForToken(request)).isNull();
    }

    @Test
    void given_undecodable_base64_when_extract_for_token_then_returns_null() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(AUTHORIZATION_HEADER, "Basic ***not-base64***");

        assertThat(extractor.extractForToken(request)).isNull();
    }

    @Test
    void given_basic_auth_without_colon_when_extract_for_token_then_returns_null() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(AUTHORIZATION_HEADER, BASIC_PREFIX + encode("no-colon-at-all"));

        assertThat(extractor.extractForToken(request)).isNull();
    }

    @Test
    void given_basic_auth_with_empty_client_id_when_extract_for_token_then_returns_null() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(AUTHORIZATION_HEADER, BASIC_PREFIX + encode(":secret-only"));

        assertThat(extractor.extractForToken(request)).isNull();
    }

    @Test
    void given_basic_auth_with_empty_secret_when_extract_for_token_then_returns_client_id() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(AUTHORIZATION_HEADER, BASIC_PREFIX + encode(CLIENT_ID + ":"));

        assertThat(extractor.extractForToken(request)).isEqualTo(CLIENT_ID);
    }

    @Test
    void given_secret_containing_colon_when_extract_for_token_then_splits_on_first_colon() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(AUTHORIZATION_HEADER, BASIC_PREFIX + encode(CLIENT_ID + ":pa:ss:word"));

        assertThat(extractor.extractForToken(request)).isEqualTo(CLIENT_ID);
    }

    private static String basic(String clientId, String secret) {
        return BASIC_PREFIX + encode(clientId + ":" + secret);
    }

    private static String encode(String raw) {
        return Base64.getEncoder().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }
}
