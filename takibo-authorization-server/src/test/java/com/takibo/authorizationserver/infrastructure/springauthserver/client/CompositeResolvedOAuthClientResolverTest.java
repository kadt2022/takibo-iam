package com.takibo.authorizationserver.infrastructure.springauthserver.client;

import com.takibo.authorizationserver.domain.client.ClientPlan;
import com.takibo.authorizationserver.domain.client.ClientType;
import com.takibo.authorizationserver.domain.client.ResolvedOAuthClient;
import com.takibo.authorizationserver.domain.client.ResolvedOAuthClientResolver;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class CompositeResolvedOAuthClientResolverTest {

    @Test
    void given_the_first_delegate_resolves_then_the_second_is_never_consulted() {
        ResolvedOAuthClient platformClient = aClient("postman-client", ClientPlan.PLATFORM);
        ResolvedOAuthClientResolver platform = clientId ->
                "postman-client".equals(clientId) ? Optional.of(platformClient) : Optional.empty();
        ResolvedOAuthClientResolver tms = clientId -> {
            throw new AssertionError("La source TMS ne doit pas etre consultee ici");
        };
        CompositeResolvedOAuthClientResolver composite =
                new CompositeResolvedOAuthClientResolver(platform, tms);

        assertThat(composite.resolve("postman-client")).contains(platformClient);
    }

    @Test
    void given_the_first_delegate_does_not_resolve_then_the_second_is_consulted() {
        ResolvedOAuthClient spaceClient = aClient("busa-finance", ClientPlan.SPACE);
        ResolvedOAuthClientResolver platform = clientId -> Optional.empty();
        ResolvedOAuthClientResolver tms = clientId ->
                "busa-finance".equals(clientId) ? Optional.of(spaceClient) : Optional.empty();
        CompositeResolvedOAuthClientResolver composite =
                new CompositeResolvedOAuthClientResolver(platform, tms);

        assertThat(composite.resolve("busa-finance")).contains(spaceClient);
    }

    @Test
    void given_no_delegate_resolves_then_the_result_is_empty() {
        ResolvedOAuthClientResolver platform = clientId -> Optional.empty();
        ResolvedOAuthClientResolver tms = clientId -> Optional.empty();
        CompositeResolvedOAuthClientResolver composite =
                new CompositeResolvedOAuthClientResolver(platform, tms);

        assertThat(composite.resolve("ghost")).isEmpty();
    }

    @Test
    void given_no_delegate_at_all_then_the_result_is_empty() {
        CompositeResolvedOAuthClientResolver composite = new CompositeResolvedOAuthClientResolver();

        assertThat(composite.resolve("anything")).isEmpty();
    }

    private static ResolvedOAuthClient aClient(String clientId, ClientPlan plan) {
        return new ResolvedOAuthClient(
                "registered-" + clientId,
                clientId,
                plan,
                plan.requiresOrganization() ? java.util.UUID.randomUUID() : null,
                plan.requiresSpace() ? java.util.UUID.randomUUID() : null,
                ClientType.CONFIDENTIAL,
                false, false, true, "hash",
                "client_secret_basic", null, null, null,
                null, null, null,
                Set.of("api.read"), Set.of("client_credentials"), Set.of(), Set.of());
    }
}
