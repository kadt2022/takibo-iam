package com.takibo.authorizationserver.infrastructure.springauthserver.client;

import com.takibo.authorizationserver.domain.client.ResolvedOAuthClient;
import com.takibo.authorizationserver.domain.client.ResolvedOAuthClientResolver;
import com.takibo.authorizationserver.infrastructure.jpa.repository.OAuth2ClientGrantTypeRepository;
import com.takibo.authorizationserver.infrastructure.jpa.repository.OAuth2ClientLookupRepository;
import com.takibo.authorizationserver.infrastructure.jpa.repository.OAuth2ClientPostLogoutRedirectUriRepository;
import com.takibo.authorizationserver.infrastructure.jpa.repository.OAuth2ClientRedirectUriRepository;
import com.takibo.authorizationserver.infrastructure.jpa.repository.OAuth2ClientScopeRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Primary;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.lang.reflect.Method;
import java.time.Clock;
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

    @Test
    void given_the_resolvedOAuthClientResolver_bean_method_then_it_is_marked_primary()
            throws NoSuchMethodException {
        // Deux beans concrets (InMemoryPlatformOAuthClientResolver, JpaResolvedOAuthClientResolver)
        // implementent aussi ResolvedOAuthClientResolver : sans @Primary, l'unicite du
        // composite pour l'autowiring par type ne tiendrait qu'a la coincidence entre le nom
        // du parametre et le nom du bean.
        Method beanMethod = ResolvedOAuthClientResolverConfig.class.getMethod(
                "resolvedOAuthClientResolver", ObjectProvider.class, JpaResolvedOAuthClientResolver.class);

        assertThat(beanMethod.isAnnotationPresent(Primary.class)).isTrue();
    }

    @Test
    void given_an_encoder_and_a_secret_then_the_platform_bean_resolves_postman_client() {
        PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
        when(passwordEncoder.encode("dev-secret")).thenReturn("encoded");

        InMemoryPlatformOAuthClientResolver platform = new ResolvedOAuthClientResolverConfig()
                .inMemoryPlatformOAuthClientResolver(passwordEncoder, "dev-secret");

        assertThat(platform.resolve("postman-client")).isPresent();
    }

    @Test
    void given_the_five_repositories_and_a_clock_then_the_tms_bean_is_built() {
        JpaResolvedOAuthClientResolver tms = new ResolvedOAuthClientResolverConfig()
                .jpaResolvedOAuthClientResolver(
                        mock(OAuth2ClientLookupRepository.class),
                        mock(OAuth2ClientGrantTypeRepository.class),
                        mock(OAuth2ClientScopeRepository.class),
                        mock(OAuth2ClientRedirectUriRepository.class),
                        mock(OAuth2ClientPostLogoutRedirectUriRepository.class),
                        mock(Clock.class));

        assertThat(tms).isNotNull();
    }
}
