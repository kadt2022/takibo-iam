package com.takibo.managementservice.domain.policy;

import com.takibo.managementservice.domain.exception.AuthorizationCodeRequiresRedirectUriException;
import com.takibo.managementservice.domain.exception.InvalidClientConfigurationException;
import com.takibo.managementservice.domain.exception.PublicAuthorizationCodeRequiresPkceException;
import com.takibo.managementservice.domain.model.ClientType;
import com.takibo.managementservice.domain.model.OAuthClientRegistration;
import com.takibo.managementservice.domain.model.TokenEndpointAuthMethod;
import com.takibo.managementservice.domain.model.ValidatedSets;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OAuthClientGrantPolicyTest {

    private static final String AUTHORIZATION_CODE = "authorization_code";
    private static final String CLIENT_CREDENTIALS = "client_credentials";
    private static final String REFRESH_TOKEN = "refresh_token";
    private static final String DEVICE_CODE =
            "urn:ietf:params:oauth:grant-type:device_code";
    private static final Set<String> REDIRECTS =
            Set.of("https://app.example/callback");
    private static final Set<String> ORIGINS = Set.of("https://app.example");

    private final OAuthClientGrantPolicy policy =
            new OAuthClientGrantPolicy();

    // ---------------------------------------------------------------
    // resolvePkce
    // ---------------------------------------------------------------

    @Test
    void defaults_pkce_for_a_public_authorization_code_client() {
        OAuthClientRegistration registration = registration(null);
        ValidatedSets sets = sets(Set.of("https://app.example"));

        assertThat(policy.resolvePkce(registration, sets)).isTrue();
    }

    @Test
    void an_explicit_pkce_choice_always_wins_over_the_default() {
        ValidatedSets sets = sets(ORIGINS);

        assertThat(policy.resolvePkce(publicClient(false), sets)).isFalse();
        assertThat(policy.resolvePkce(confidentialClient(true), sets)).isTrue();
    }

    @Test
    void does_not_default_pkce_for_a_confidential_authorization_code_client() {
        assertThat(policy.resolvePkce(confidentialClient(null), sets(Set.of())))
                .isFalse();
    }

    @Test
    void does_not_default_pkce_for_a_public_client_without_authorization_code() {
        ValidatedSets deviceOnly = new ValidatedSets(
                Set.of(DEVICE_CODE), Set.of(), Set.of(), Set.of(), Set.of()
        );

        assertThat(policy.resolvePkce(publicClient(null), deviceOnly)).isFalse();
    }

    // ---------------------------------------------------------------
    // authorization_code (tous types de clients)
    // ---------------------------------------------------------------

    @ParameterizedTest
    @EnumSource(ClientType.class)
    void authorization_code_requires_at_least_one_redirect_uri(ClientType type) {
        OAuthClientRegistration registration = type == ClientType.PUBLIC
                ? publicClient(true)
                : confidentialClient(false);
        TokenEndpointAuthMethod method = type == ClientType.PUBLIC
                ? TokenEndpointAuthMethod.none
                : TokenEndpointAuthMethod.client_secret_basic;
        ValidatedSets withoutRedirect = new ValidatedSets(
                Set.of(AUTHORIZATION_CODE), Set.of(), Set.of(), Set.of(), ORIGINS
        );

        assertThatThrownBy(() -> policy.validateGrantProfile(
                registration, method, withoutRedirect, true
        )).isInstanceOf(AuthorizationCodeRequiresRedirectUriException.class);
    }

    @Test
    void a_public_authorization_code_client_without_pkce_is_refused() {
        OAuthClientRegistration withoutPkce = publicClient(false);
        ValidatedSets sets = sets(ORIGINS);

        assertThatThrownBy(() -> policy.validateGrantProfile(
                withoutPkce,
                TokenEndpointAuthMethod.none,
                sets,
                false
        )).isInstanceOf(PublicAuthorizationCodeRequiresPkceException.class);
    }

    @Test
    void a_public_client_that_did_not_choose_pkce_gets_it_by_default_and_passes() {
        OAuthClientRegistration registration = publicClient(null);
        ValidatedSets sets = sets(ORIGINS);
        boolean pkce = policy.resolvePkce(registration, sets);

        assertThatCode(() -> policy.validateGrantProfile(
                registration, TokenEndpointAuthMethod.none, sets, pkce
        )).doesNotThrowAnyException();
    }

    // ---------------------------------------------------------------
    // client_credentials
    // ---------------------------------------------------------------

    @ParameterizedTest
    @ValueSource(strings = {AUTHORIZATION_CODE, REFRESH_TOKEN, DEVICE_CODE})
    void client_credentials_cannot_be_combined_with_another_grant(String other) {
        // Les redirections sont fournies pour que la règle authorization_code
        // (évaluée avant) ne masque pas le refus de combinaison.
        ValidatedSets sets = new ValidatedSets(
                Set.of(CLIENT_CREDENTIALS, other), Set.of(), REDIRECTS, Set.of(), Set.of()
        );

        assertInvalid(
                confidentialMachine(null),
                TokenEndpointAuthMethod.client_secret_basic,
                sets,
                "client_credentials cannot be combined with other grant types"
        );
    }

    @Test
    void client_credentials_requires_a_confidential_client() {
        assertInvalid(
                publicMachine(),
                TokenEndpointAuthMethod.none,
                machineSets(Set.of(CLIENT_CREDENTIALS)),
                "client_credentials requires clientType=CONFIDENTIAL"
        );
    }

    @Test
    void client_credentials_requires_client_authentication() {
        assertInvalid(
                confidentialMachine(null),
                TokenEndpointAuthMethod.none,
                machineSets(Set.of(CLIENT_CREDENTIALS)),
                "client_credentials requires client authentication"
        );
    }

    @Test
    void client_credentials_must_not_request_pkce() {
        assertInvalid(
                confidentialMachine(true),
                TokenEndpointAuthMethod.client_secret_basic,
                machineSets(Set.of(CLIENT_CREDENTIALS)),
                "client_credentials must not use PKCE"
        );
    }

    @Test
    void client_credentials_must_not_declare_a_redirect_uri() {
        assertMachineUrisRefused(new ValidatedSets(
                Set.of(CLIENT_CREDENTIALS), Set.of(), REDIRECTS, Set.of(), Set.of()
        ));
    }

    @Test
    void client_credentials_must_not_declare_a_post_logout_redirect_uri() {
        assertMachineUrisRefused(new ValidatedSets(
                Set.of(CLIENT_CREDENTIALS), Set.of(), Set.of(),
                Set.of("https://app.example/logout"), Set.of()
        ));
    }

    @Test
    void client_credentials_must_not_declare_a_cors_origin() {
        assertMachineUrisRefused(new ValidatedSets(
                Set.of(CLIENT_CREDENTIALS), Set.of(), Set.of(), Set.of(), ORIGINS
        ));
    }

    @ParameterizedTest
    @EnumSource(
            value = TokenEndpointAuthMethod.class,
            names = "none",
            mode = EnumSource.Mode.EXCLUDE
    )
    void accepts_an_authenticated_confidential_machine_client(
            TokenEndpointAuthMethod method
    ) {
        assertThatCode(() -> policy.validateGrantProfile(
                confidentialMachine(false),
                method,
                machineSets(Set.of(CLIENT_CREDENTIALS)),
                false
        )).doesNotThrowAnyException();
    }

    // ---------------------------------------------------------------
    // Clients PUBLIC (SPA)
    // ---------------------------------------------------------------

    @Test
    void accepts_a_complete_public_spa_profile() {
        OAuthClientRegistration registration = registration(true);
        ValidatedSets sets = sets(Set.of("https://app.example"));

        policy.validateGrantProfile(
                registration,
                TokenEndpointAuthMethod.none,
                sets,
                true
        );
    }

    @Test
    void rejects_a_public_spa_without_a_cors_origin() {
        OAuthClientRegistration registration = registration(true);
        ValidatedSets sets = sets(Set.of());

        assertThatThrownBy(() -> policy.validateGrantProfile(
                registration,
                TokenEndpointAuthMethod.none,
                sets,
                true
        ))
                .isInstanceOf(InvalidClientConfigurationException.class)
                .hasMessage("PUBLIC clients should declare corsOrigins");
    }

    @ParameterizedTest
    @EnumSource(
            value = TokenEndpointAuthMethod.class,
            names = "none",
            mode = EnumSource.Mode.EXCLUDE
    )
    void a_public_client_cannot_authenticate_at_the_token_endpoint(
            TokenEndpointAuthMethod method
    ) {
        assertInvalid(
                publicClient(true),
                method,
                sets(ORIGINS),
                "PUBLIC clients must use token_endpoint_auth_method=none"
        );
    }

    @Test
    void a_public_client_cannot_require_a_client_secret() {
        OAuthClientRegistration registration = publicClient(true, true);

        assertInvalid(
                registration,
                TokenEndpointAuthMethod.none,
                sets(ORIGINS),
                "PUBLIC clients must not require clientSecret"
        );
    }

    @Test
    void a_public_client_without_authorization_code_needs_neither_pkce_nor_cors() {
        ValidatedSets deviceOnly = new ValidatedSets(
                Set.of(DEVICE_CODE), Set.of("api:read"), Set.of(), Set.of(), Set.of()
        );

        assertThatCode(() -> policy.validateGrantProfile(
                publicClient(null),
                TokenEndpointAuthMethod.none,
                deviceOnly,
                false
        )).doesNotThrowAnyException();
    }

    // ---------------------------------------------------------------
    // Clients CONFIDENTIAL en authorization_code
    // ---------------------------------------------------------------

    @Test
    void a_confidential_authorization_code_client_must_authenticate() {
        assertInvalid(
                confidentialClient(null),
                TokenEndpointAuthMethod.none,
                sets(Set.of()),
                "CONFIDENTIAL authorization_code requires client authentication"
        );
    }

    @ParameterizedTest
    @EnumSource(
            value = TokenEndpointAuthMethod.class,
            names = {"client_secret_basic", "client_secret_post", "client_secret_jwt"}
    )
    void a_secret_based_confidential_client_cannot_opt_out_of_its_secret(
            TokenEndpointAuthMethod method
    ) {
        assertInvalid(
                confidentialClient(null, false),
                method,
                sets(Set.of()),
                "CONFIDENTIAL clients require clientSecret"
        );
    }

    @Test
    void a_private_key_jwt_confidential_client_may_opt_out_of_a_secret() {
        assertThatCode(() -> policy.validateGrantProfile(
                confidentialClient(null, false),
                TokenEndpointAuthMethod.private_key_jwt,
                sets(Set.of()),
                false
        )).doesNotThrowAnyException();
    }

    @Test
    void a_confidential_client_with_an_unspecified_secret_requirement_is_accepted() {
        assertThatCode(() -> policy.validateGrantProfile(
                confidentialClient(null, null),
                TokenEndpointAuthMethod.client_secret_basic,
                sets(Set.of()),
                false
        )).doesNotThrowAnyException();
    }

    @Test
    void a_confidential_client_may_also_require_pkce() {
        assertThatCode(() -> policy.validateGrantProfile(
                confidentialClient(true),
                TokenEndpointAuthMethod.client_secret_post,
                sets(Set.of()),
                true
        )).doesNotThrowAnyException();
    }

    @Test
    void leaves_authentication_of_a_non_authorization_code_confidential_client_to_the_authentication_policy() {
        ValidatedSets refreshOnly = new ValidatedSets(
                Set.of(REFRESH_TOKEN), Set.of(), Set.of(), Set.of(), Set.of()
        );

        assertThatCode(() -> policy.validateGrantProfile(
                confidentialClient(null, false),
                TokenEndpointAuthMethod.none,
                refreshOnly,
                false
        )).doesNotThrowAnyException();
    }

    // ---------------------------------------------------------------
    // Outils
    // ---------------------------------------------------------------

    private void assertMachineUrisRefused(ValidatedSets sets) {
        assertInvalid(
                confidentialMachine(false),
                TokenEndpointAuthMethod.client_secret_basic,
                sets,
                "client_credentials must not include redirect/cors/post-logout URIs"
        );
    }

    private void assertInvalid(
            OAuthClientRegistration registration,
            TokenEndpointAuthMethod method,
            ValidatedSets sets,
            String message
    ) {
        boolean requirePkce = Boolean.TRUE.equals(registration.requirePkce());

        assertThatThrownBy(() -> policy.validateGrantProfile(
                registration,
                method,
                sets,
                requirePkce
        ))
                .isInstanceOf(InvalidClientConfigurationException.class)
                .hasMessage(message);
    }

    private static OAuthClientRegistration registration(Boolean requirePkce) {
        return publicClient(requirePkce);
    }

    private static OAuthClientRegistration publicClient(Boolean requirePkce) {
        return publicClient(requirePkce, false);
    }

    private static OAuthClientRegistration publicClient(
            Boolean requirePkce,
            Boolean requireSecret
    ) {
        return registration(
                ClientType.PUBLIC,
                requireSecret,
                TokenEndpointAuthMethod.none,
                requirePkce,
                Set.of(AUTHORIZATION_CODE)
        );
    }

    private static OAuthClientRegistration confidentialClient(Boolean requirePkce) {
        return confidentialClient(requirePkce, true);
    }

    private static OAuthClientRegistration confidentialClient(
            Boolean requirePkce,
            Boolean requireSecret
    ) {
        return registration(
                ClientType.CONFIDENTIAL,
                requireSecret,
                TokenEndpointAuthMethod.client_secret_basic,
                requirePkce,
                Set.of(AUTHORIZATION_CODE)
        );
    }

    private static OAuthClientRegistration confidentialMachine(Boolean requirePkce) {
        return registration(
                ClientType.CONFIDENTIAL,
                true,
                TokenEndpointAuthMethod.client_secret_basic,
                requirePkce,
                Set.of(CLIENT_CREDENTIALS)
        );
    }

    private static OAuthClientRegistration publicMachine() {
        return registration(
                ClientType.PUBLIC,
                false,
                TokenEndpointAuthMethod.none,
                null,
                Set.of(CLIENT_CREDENTIALS)
        );
    }

    private static OAuthClientRegistration registration(
            ClientType type,
            Boolean requireSecret,
            TokenEndpointAuthMethod method,
            Boolean requirePkce,
            Set<String> grantTypes
    ) {
        return new OAuthClientRegistration(
                "client-test",
                "Client Test",
                type,
                requireSecret,
                method,
                requirePkce,
                false,
                null,
                null,
                "RS256",
                900,
                3600,
                900,
                null,
                Set.of("api:read"),
                grantTypes,
                REDIRECTS,
                Set.of(),
                ORIGINS
        );
    }

    private static ValidatedSets sets(Set<String> corsOrigins) {
        return new ValidatedSets(
                Set.of("authorization_code"),
                Set.of("api:read"),
                Set.of("https://app.example/callback"),
                Set.of(),
                corsOrigins
        );
    }

    private static ValidatedSets machineSets(Set<String> grantTypes) {
        return new ValidatedSets(
                grantTypes, Set.of("api:read"), Set.of(), Set.of(), Set.of()
        );
    }
}
