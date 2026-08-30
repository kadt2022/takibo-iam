package com.takibo.authorizationserver.infrastructure.springauthserver.client;

import com.takibo.authorizationserver.domain.client.ClientPlan;
import com.takibo.authorizationserver.domain.client.ClientType;
import com.takibo.authorizationserver.domain.client.ResolvedOAuthClient;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class InMemoryPlatformOAuthClientResolverTest {

    private final PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);

    @Test
    void given_postman_client_id_then_it_resolves_as_platform() {
        when(passwordEncoder.encode(eq("dev-secret"))).thenReturn("encoded-secret");
        InMemoryPlatformOAuthClientResolver resolver =
                new InMemoryPlatformOAuthClientResolver(passwordEncoder, "dev-secret");

        Optional<ResolvedOAuthClient> resolved = resolver.resolve("postman-client");

        assertThat(resolved).isPresent();
        ResolvedOAuthClient client = resolved.get();
        assertThat(client.clientId()).isEqualTo("postman-client");
        assertThat(client.plan()).isEqualTo(ClientPlan.PLATFORM);
        assertThat(client.orgId()).isNull();
        assertThat(client.spaceId()).isNull();
        assertThat(client.clientType()).isEqualTo(ClientType.CONFIDENTIAL);
        assertThat(client.clientSecretHash()).isEqualTo("encoded-secret");
        assertThat(client.scopes()).containsExactlyInAnyOrder("api.read", "api.write");
        assertThat(client.grantTypes()).containsExactly("client_credentials");
        assertThat(client.tokenEndpointAuthMethod()).isEqualTo("client_secret_basic");
    }

    @Test
    void given_any_other_client_id_then_nothing_resolves() {
        when(passwordEncoder.encode(eq("dev-secret"))).thenReturn("encoded-secret");
        InMemoryPlatformOAuthClientResolver resolver =
                new InMemoryPlatformOAuthClientResolver(passwordEncoder, "dev-secret");

        assertThat(resolver.resolve("some-other-client")).isEmpty();
    }

    @Test
    void given_two_separate_resolver_instances_then_the_registered_client_id_is_identical() {
        // Simule deux redemarrages : OAuth2AuthorizationService (TAS-GRANTS-02) persiste
        // registeredClientId et le relit apres coup. Un UUID different a chaque instance
        // romprait ce lien des le premier redemarrage.
        when(passwordEncoder.encode("dev-secret")).thenReturn("encoded-secret");
        InMemoryPlatformOAuthClientResolver first =
                new InMemoryPlatformOAuthClientResolver(passwordEncoder, "dev-secret");
        InMemoryPlatformOAuthClientResolver second =
                new InMemoryPlatformOAuthClientResolver(passwordEncoder, "dev-secret");

        assertThat(first.resolve("postman-client").orElseThrow().registeredClientId())
                .isEqualTo(second.resolve("postman-client").orElseThrow().registeredClientId());
    }

    @Test
    void given_two_resolutions_then_the_same_instance_is_returned_without_re_encoding() {
        // Le secret est code une seule fois, a la construction : verifie qu'aucun second
        // appel au PasswordEncoder n'a lieu a la resolution.
        when(passwordEncoder.encode(eq("dev-secret"))).thenReturn("encoded-secret");
        InMemoryPlatformOAuthClientResolver resolver =
                new InMemoryPlatformOAuthClientResolver(passwordEncoder, "dev-secret");

        ResolvedOAuthClient first = resolver.resolve("postman-client").orElseThrow();
        ResolvedOAuthClient second = resolver.resolve("postman-client").orElseThrow();

        assertThat(first).isSameAs(second);
        verify(passwordEncoder, times(1)).encode("dev-secret");
    }
}
