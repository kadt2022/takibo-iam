package com.takibo.managementservice.domain.policy;

import com.takibo.managementservice.domain.exception.InvalidClientConfigurationException;
import com.takibo.managementservice.domain.exception.PublicClientAuthMethodNotNoneException;
import com.takibo.managementservice.domain.exception.PublicClientMustNotHaveSecretException;
import com.takibo.managementservice.domain.model.ClientType;
import com.takibo.managementservice.domain.model.OAuthClientRegistration;
import com.takibo.managementservice.domain.model.TokenEndpointAuthMethod;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OAuthClientAuthenticationPolicyTest {

    private final OAuthClientAuthenticationPolicy policy =
            new OAuthClientAuthenticationPolicy();

    @Test
    void defaults_public_clients_to_no_authentication_and_no_secret() {
        OAuthClientRegistration registration = registration(
                ClientType.PUBLIC,
                false,
                null
        );

        TokenEndpointAuthMethod method =
                policy.resolveAuthMethod(registration);

        assertThat(method).isEqualTo(TokenEndpointAuthMethod.none);
        assertThat(policy.requiresSecret(registration, method)).isFalse();
    }

    @Test
    void defaults_confidential_clients_to_basic_secret_authentication() {
        OAuthClientRegistration registration = registration(
                ClientType.CONFIDENTIAL,
                true,
                null
        );

        TokenEndpointAuthMethod method =
                policy.resolveAuthMethod(registration);

        assertThat(method)
                .isEqualTo(TokenEndpointAuthMethod.client_secret_basic);
        assertThat(policy.requiresSecret(registration, method)).isTrue();
    }

    @Test
    void rejects_a_secret_on_a_public_client() {
        OAuthClientRegistration registration = registration(
                ClientType.PUBLIC,
                true,
                TokenEndpointAuthMethod.none
        );

        assertThatThrownBy(() ->
                policy.validateAuthenticationProfile(
                        registration,
                        TokenEndpointAuthMethod.none
                )
        ).isInstanceOf(PublicClientMustNotHaveSecretException.class);
    }

    @ParameterizedTest
    @EnumSource(TokenEndpointAuthMethod.class)
    void an_explicit_authentication_method_is_never_overridden(
            TokenEndpointAuthMethod method
    ) {
        assertThat(policy.resolveAuthMethod(
                registration(ClientType.PUBLIC, false, method)
        )).isEqualTo(method);
        assertThat(policy.resolveAuthMethod(
                registration(ClientType.CONFIDENTIAL, true, method)
        )).isEqualTo(method);
    }

    @ParameterizedTest
    @EnumSource(
            value = TokenEndpointAuthMethod.class,
            names = "none",
            mode = EnumSource.Mode.EXCLUDE
    )
    void a_public_client_cannot_use_any_client_authentication(
            TokenEndpointAuthMethod method
    ) {
        OAuthClientRegistration registration =
                registration(ClientType.PUBLIC, false, method);

        assertThatThrownBy(() ->
                policy.validateAuthenticationProfile(registration, method)
        )
                .isInstanceOf(PublicClientAuthMethodNotNoneException.class)
                .hasMessageContaining("(found: " + method.name() + ")");
    }

    @Test
    void accepts_a_public_client_without_authentication_nor_secret() {
        OAuthClientRegistration registration = registration(
                ClientType.PUBLIC, null, TokenEndpointAuthMethod.none
        );

        assertThatCode(() -> policy.validateAuthenticationProfile(
                registration, TokenEndpointAuthMethod.none
        )).doesNotThrowAnyException();
    }

    @Test
    void a_confidential_client_cannot_skip_client_authentication() {
        OAuthClientRegistration registration = registration(
                ClientType.CONFIDENTIAL, true, TokenEndpointAuthMethod.none
        );

        assertThatThrownBy(() -> policy.validateAuthenticationProfile(
                registration, TokenEndpointAuthMethod.none
        ))
                .isInstanceOf(InvalidClientConfigurationException.class)
                .hasMessage("Confidential clients require clientSecret "
                        + "(token_endpoint_auth_method cannot be none)");
    }

    @ParameterizedTest
    @EnumSource(
            value = TokenEndpointAuthMethod.class,
            names = {"client_secret_basic", "client_secret_post", "client_secret_jwt"}
    )
    void a_secret_based_confidential_client_cannot_refuse_a_secret(
            TokenEndpointAuthMethod method
    ) {
        OAuthClientRegistration registration =
                registration(ClientType.CONFIDENTIAL, false, method);

        assertThatThrownBy(() ->
                policy.validateAuthenticationProfile(registration, method)
        )
                .isInstanceOf(InvalidClientConfigurationException.class)
                .hasMessage("Confidential clients require clientSecret "
                        + "(requireClientSecret=true)");
    }

    @Test
    void a_private_key_jwt_confidential_client_may_refuse_a_secret() {
        OAuthClientRegistration registration = registration(
                ClientType.CONFIDENTIAL, false, TokenEndpointAuthMethod.private_key_jwt
        );

        assertThatCode(() -> policy.validateAuthenticationProfile(
                registration, TokenEndpointAuthMethod.private_key_jwt
        )).doesNotThrowAnyException();
        assertThat(policy.requiresSecret(
                registration, TokenEndpointAuthMethod.private_key_jwt
        )).isFalse();
    }

    @Test
    void a_confidential_client_with_an_unspecified_secret_requirement_is_accepted() {
        OAuthClientRegistration registration = registration(
                ClientType.CONFIDENTIAL, null, TokenEndpointAuthMethod.client_secret_post
        );

        assertThatCode(() -> policy.validateAuthenticationProfile(
                registration, TokenEndpointAuthMethod.client_secret_post
        )).doesNotThrowAnyException();
    }

    @ParameterizedTest
    @CsvSource({
            "client_secret_basic, true",
            "client_secret_post,  true",
            "client_secret_jwt,   true",
            "private_key_jwt,     false",
            "none,                false"
    })
    void only_secret_based_methods_use_a_shared_secret(
            TokenEndpointAuthMethod method,
            boolean usesSecret
    ) {
        assertThat(policy.usesSecret(method)).isEqualTo(usesSecret);
    }

    @ParameterizedTest
    @CsvSource({
            "client_secret_basic, , true",
            "client_secret_jwt, false, true",
            "private_key_jwt, , false",
            "private_key_jwt, true, true"
    })
    void a_confidential_client_needs_a_secret_when_asked_or_when_its_method_uses_one(
            TokenEndpointAuthMethod method,
            Boolean requireSecret,
            boolean expected
    ) {
        OAuthClientRegistration registration =
                registration(ClientType.CONFIDENTIAL, requireSecret, method);

        assertThat(policy.requiresSecret(registration, method))
                .isEqualTo(expected);
    }

    @Test
    void a_public_client_never_receives_a_secret_whatever_it_asks() {
        OAuthClientRegistration registration = registration(
                ClientType.PUBLIC, true, TokenEndpointAuthMethod.client_secret_basic
        );

        assertThat(policy.requiresSecret(
                registration, TokenEndpointAuthMethod.client_secret_basic
        )).isFalse();
    }

    private static OAuthClientRegistration registration(
            ClientType type,
            Boolean requireSecret,
            TokenEndpointAuthMethod method
    ) {
        return new OAuthClientRegistration(
                "client-test",
                "Client Test",
                type,
                requireSecret,
                method,
                true,
                false,
                null,
                null,
                "RS256",
                900,
                3600,
                900,
                null,
                Set.of("api:read"),
                Set.of("authorization_code"),
                Set.of("https://app.example/callback"),
                Set.of(),
                Set.of("https://app.example")
        );
    }
}
