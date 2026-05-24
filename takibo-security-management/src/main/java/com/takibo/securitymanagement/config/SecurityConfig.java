package com.takibo.securitymanagement.config;

import com.takibo.securitymanagement.infrastructure.adp.PolicyBasedAuthorizationManager;
import com.takibo.securitymanagement.infrastructure.security.JwtAuthenticationFilter;
import com.takibo.securitymanagement.infrastructure.security.boundary.OrgBoundaryFilter;
import com.takibo.securitymanagement.sentinel.http.SentinelAccessDeniedHandler;
import com.takibo.securitymanagement.sentinel.http.SentinelAuthenticationEntryPoint;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final OrgBoundaryFilter orgBoundaryFilter;
    private final PolicyBasedAuthorizationManager policyBasedAuthorizationManager;
    private final SentinelAuthenticationEntryPoint sentinelAuthenticationEntryPoint;
    private final SentinelAccessDeniedHandler sentinelAccessDeniedHandler;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {

        http
                .cors(Customizer.withDefaults())
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(sentinelAuthenticationEntryPoint)
                        .accessDeniedHandler(sentinelAccessDeniedHandler)
                )

                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()

                        .requestMatchers(
                                "/error",
                                "/favicon.ico",
                                "/swagger-ui/**",
                                "/v3/api-docs/**",
                                "/actuator/**",
                                "/api/public/**",
                                "/api/auth/**"
                        ).permitAll()

                        .requestMatchers("/api/platform/**").hasRole("PLATFORM_ADMIN")

                        .requestMatchers("/api/organizations/**").access(policyBasedAuthorizationManager)
                        .requestMatchers("/api/spaces/**").access(policyBasedAuthorizationManager)
                        .requestMatchers("/api/users/**").access(policyBasedAuthorizationManager)
                        .requestMatchers("/api/roles/**").access(policyBasedAuthorizationManager)
                        .requestMatchers("/api/secrets/**").access(policyBasedAuthorizationManager)
                        .requestMatchers("/api/permissions/**").access(policyBasedAuthorizationManager)
                        .requestMatchers("/api/v1/**").access(policyBasedAuthorizationManager)
                        .requestMatchers("/debug/secure/**").access(policyBasedAuthorizationManager)

                        .anyRequest().authenticated()
                );

        http
                .addFilterBefore(jwtAuthenticationFilter, AnonymousAuthenticationFilter.class)
                .addFilterAfter(orgBoundaryFilter, JwtAuthenticationFilter.class);

        return http.build();
    }
}
