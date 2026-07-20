package com.takibo.authorizationserver.infrastructure.springauthserver.config;

import com.takibo.authorizationserver.infrastructure.springauthserver.client.TakiboJwtClientAssertionDecoderFactory;
import com.takibo.authorizationserver.infrastructure.springauthserver.properties.TasAuthorizationServerProperties;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.oauth2.server.authorization.OAuth2AuthorizationServerConfigurer;
import org.springframework.security.oauth2.server.authorization.authentication.JwtClientAssertionAuthenticationProvider;
import org.springframework.security.oauth2.server.authorization.settings.AuthorizationServerSettings;
import org.springframework.security.web.DefaultSecurityFilterChain;
import org.springframework.security.web.util.matcher.RequestMatcher;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TakiboAuthorizationServerConfigurationTest {

    @Test
    void wires_custom_decoder_factory_into_client_assertion_provider() {
        JwtClientAssertionAuthenticationProvider assertionProvider =
                mock(JwtClientAssertionAuthenticationProvider.class);
        AuthenticationProvider otherProvider = mock(AuthenticationProvider.class);
        TakiboJwtClientAssertionDecoderFactory decoderFactory =
                new TakiboJwtClientAssertionDecoderFactory();

        TakiboAuthorizationServerConfiguration.configureJwtDecoderFactory(
                List.of(otherProvider, assertionProvider), decoderFactory);

        verify(assertionProvider).setJwtDecoderFactory(decoderFactory);
    }

    @Test
    @SuppressWarnings("unchecked")
    void builds_authorization_server_filter_chain_with_decoder_customization() throws Exception {
        HttpSecurity http = mock(HttpSecurity.class);
        DefaultSecurityFilterChain filterChain = mock(DefaultSecurityFilterChain.class);
        TakiboJwtClientAssertionDecoderFactory decoderFactory =
                new TakiboJwtClientAssertionDecoderFactory();
        when(http.securityMatcher(any(RequestMatcher.class))).thenReturn(http);
        when(http.with(
                any(OAuth2AuthorizationServerConfigurer.class),
                any(Customizer.class)))
                .thenAnswer(invocation -> {
                    OAuth2AuthorizationServerConfigurer configurer = invocation.getArgument(0);
                    Customizer<OAuth2AuthorizationServerConfigurer> customizer = invocation.getArgument(1);
                    customizer.customize(configurer);
                    return http;
                });
        when(http.authorizeHttpRequests(any(Customizer.class))).thenReturn(http);
        when(http.csrf(any(Customizer.class))).thenReturn(http);
        when(http.build()).thenReturn(filterChain);

        assertThat(new TakiboAuthorizationServerConfiguration()
                .authorizationServerSecurityFilterChain(http, decoderFactory))
                .isSameAs(filterChain);
    }

    @Test
    void builds_authorization_server_settings_from_properties() {
        TasAuthorizationServerProperties properties = new TasAuthorizationServerProperties();
        properties.setIssuer("https://id.example");

        AuthorizationServerSettings settings = new TakiboAuthorizationServerConfiguration()
                .authorizationServerSettings(properties);

        assertThat(settings.getIssuer()).isEqualTo("https://id.example");
    }
}
