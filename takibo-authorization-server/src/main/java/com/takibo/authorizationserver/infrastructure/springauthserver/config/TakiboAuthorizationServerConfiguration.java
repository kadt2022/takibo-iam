package com.takibo.authorizationserver.infrastructure.springauthserver.config;

import com.takibo.authorizationserver.infrastructure.springauthserver.properties.TasAuthorizationServerProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.oauth2.server.authorization.OAuth2AuthorizationServerConfigurer;
import org.springframework.security.oauth2.server.authorization.settings.AuthorizationServerSettings;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.util.matcher.RequestMatcher;

@Configuration
@EnableConfigurationProperties(TasAuthorizationServerProperties.class)
public class TakiboAuthorizationServerConfiguration {

    @Bean
    @Order(1)
    public SecurityFilterChain authorizationServerSecurityFilterChain(HttpSecurity http) throws Exception {
        OAuth2AuthorizationServerConfigurer authorizationServerConfigurer = new OAuth2AuthorizationServerConfigurer();
        RequestMatcher endpointsMatcher = authorizationServerConfigurer.getEndpointsMatcher();

        http
                .securityMatcher(endpointsMatcher)
                .with(authorizationServerConfigurer, Customizer.withDefaults())
                .authorizeHttpRequests(authorize -> authorize.anyRequest().authenticated())
                .csrf(csrf -> csrf.ignoringRequestMatchers(endpointsMatcher));

        return http.build();
    }

    @Bean
    public AuthorizationServerSettings authorizationServerSettings(TasAuthorizationServerProperties properties) {
        return AuthorizationServerSettings.builder()
                .issuer(properties.getIssuer())
                .build();
    }
}
