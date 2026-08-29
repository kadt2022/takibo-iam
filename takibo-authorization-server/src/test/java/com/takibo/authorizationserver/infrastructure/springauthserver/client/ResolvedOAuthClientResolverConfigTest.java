package com.takibo.authorizationserver.infrastructure.springauthserver.client;

import com.takibo.authorizationserver.domain.client.ResolvedOAuthClient;
import com.takibo.authorizationserver.domain.client.ResolvedOAuthClientResolver;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ResolvedOAuthClientResolverConfigTest {

    @SuppressWarnings("unchecked")
    private final ObjectProvider<InMemoryPlatformOAuthClientResolver> platformProvider =
            mock(ObjectProvider.class);

    @Test
    void given_a_platform_resolver_present_then_it_is_checked_before_tms() {
        InMemoryPlatformOAuthClientResolver platform = mock(InMemoryPlatformOAuthClientResolver.class);
        when(platformProvider.getIfAvailable()).thenReturn(platform);
        ResolvedOAuthClient platformClient = mock(ResolvedOAuthClient.class);
        when(platform.resolve("postman-client")).thenReturn(Optional.of(platformClient));
        JpaResolvedOAuthClientResolver tms = mock(JpaResolvedOAuthClientResolver.class);

        ResolvedOAuthClientResolver resolver =
                new ResolvedOAuthClientResolverConfig().resolvedOAuthClientResolver(platformProvider, tms);

        assertThat(resolver).isInstanceOf(CompositeResolvedOAuthClientResolver.class);
        assertThat(resolver.resolve("postman-client")).contains(platformClient);
        verify(tms, never()).resolve("postman-client");
    }

    @Test
    void given_no_platform_resolver_in_context_then_only_tms_is_used() {
        // Profil non-dev : le bean @Profile("dev") est absent, ObjectProvider le dit.
        when(platformProvider.getIfAvailable()).thenReturn(null);
        JpaResolvedOAuthClientResolver tms = mock(JpaResolvedOAuthClientResolver.class);
        ResolvedOAuthClient spaceClient = mock(ResolvedOAuthClient.class);
        when(tms.resolve("busa-finance")).thenReturn(Optional.of(spaceClient));

        ResolvedOAuthClientResolver resolver =
                new ResolvedOAuthClientResolverConfig().resolvedOAuthClientResolver(platformProvider, tms);

        assertThat(resolver.resolve("busa-finance")).contains(spaceClient);
    }
}
