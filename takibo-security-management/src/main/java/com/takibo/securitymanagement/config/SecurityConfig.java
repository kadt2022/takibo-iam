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
import org.springframework.security.web.access.intercept.AuthorizationFilter;
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
                        .requestMatchers(  "/api/v1/**").access(policyBasedAuthorizationManager)
                        .requestMatchers("/debug/secure/**").access(policyBasedAuthorizationManager) // DEBUG A suprrimer


                        .anyRequest().authenticated()
                );

                /*
                 Ordre CRITIQUE :
                 - JWT doit passer AVANT AnonymousAuthenticationFilter, sinon Spring met un contexte anonyme.
                 - JWT doit passer AVANT AuthorizationFilter, sinon les règles d’accès voient un user anonyme.
                 - Boundary après JWT (car il a besoin du contexte) et donc avant AuthorizationFilter.
                 */
        http
                // JWT d'abord : construit l'Authentication (JwtAuthenticationToken)
                .addFilterBefore(jwtAuthenticationFilter, AnonymousAuthenticationFilter.class)
                .addFilterBefore(jwtAuthenticationFilter, AuthorizationFilter.class)

                // Boundary après JWT, mais avant toute autorisation (ADP)
                .addFilterAfter(orgBoundaryFilter, JwtAuthenticationFilter.class)
                .addFilterBefore(orgBoundaryFilter, AuthorizationFilter.class);

        return http.build();
    }
}
