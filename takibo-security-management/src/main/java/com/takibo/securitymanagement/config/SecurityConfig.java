package com.takibo.securitymanagement.config;

import com.takibo.identitycore.domain.catalogrbac.TechnicalRole;
import com.takibo.securitymanagement.infrastructure.adp.PolicyBasedAuthorizationManager;
import com.takibo.securitymanagement.infrastructure.security.JwtAuthenticationFilter;
import com.takibo.securitymanagement.infrastructure.security.boundary.OrgBoundaryFilter;
import com.takibo.securitymanagement.sentinel.http.SentinelAccessDeniedHandler;
import com.takibo.securitymanagement.sentinel.http.SentinelAuthenticationEntryPoint;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;
import org.springframework.web.cors.CorsConfigurationSource;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private static final String PLATFORM_ADMIN_AUTHORITY =
            "ROLE_" + TechnicalRole.PLATFORM_ADMIN.code();

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final OrgBoundaryFilter orgBoundaryFilter;
    private final PolicyBasedAuthorizationManager policyBasedAuthorizationManager;
    private final SentinelAuthenticationEntryPoint sentinelAuthenticationEntryPoint;
    private final SentinelAccessDeniedHandler sentinelAccessDeniedHandler;
    private final CorsConfigurationSource corsConfigurationSource;

    // Documentation OpenAPI : publique seulement là où Springdoc la sert (profil dev). Ailleurs,
    // ses chemins retombent sur le refus par défaut au lieu d'être ouverts sur du vide.
    @Value("${springdoc.api-docs.enabled:false}")
    private boolean openApiEnabled;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {

        http
                // SEC-TMS-05 : source déclarée, jamais résolue d'office par nom ou par type.
                .cors(cors -> cors.configurationSource(corsConfigurationSource))
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(sentinelAuthenticationEntryPoint)
                        .accessDeniedHandler(sentinelAccessDeniedHandler)
                )

                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()

                        .requestMatchers("/error", "/favicon.ico").permitAll()
                        .requestMatchers("/swagger-ui/**", "/swagger-ui.html", "/v3/api-docs/**")
                        .access((authentication, request) -> new AuthorizationDecision(openApiEnabled))

                        // Public probes expose only the aggregate status. Every other
                        // Actuator endpoint is reserved for platform administration.
                        .requestMatchers(HttpMethod.GET,
                                "/actuator/health",
                                "/actuator/health/liveness",
                                "/actuator/health/readiness"
                        ).permitAll()
                        .requestMatchers("/actuator/**")
                        .hasAuthority(PLATFORM_ADMIN_AUTHORITY)

                        // Login humain : public par nature (l'appelant n'a pas encore de token).
                        // Volontairement limité à cette route exacte — pas tout /api/v1/auth/**.
                        .requestMatchers(HttpMethod.POST, "/api/v1/auth/login").permitAll()

                        .requestMatchers("/api/v1/**").access(policyBasedAuthorizationManager)
                        .requestMatchers("/debug/secure/**").access(policyBasedAuthorizationManager)

                        // Fail-closed : une route absente de cette liste est refusée, même
                        // authentifiée. Un nouveau contrôleur doit être déclaré ici pour être servi.
                        .anyRequest().denyAll()
                );

        http
                .addFilterBefore(jwtAuthenticationFilter, AnonymousAuthenticationFilter.class)
                .addFilterAfter(orgBoundaryFilter, JwtAuthenticationFilter.class);

        return http.build();
    }
}
