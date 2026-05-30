package com.takibo.adp.test.config;

import com.takibo.adp.api.*;
import com.takibo.adp.spring.adapter.AdpContextEnricher;
import com.takibo.adp.spring.adapter.RequestVelocityTracker;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;

import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.stream.Collectors;

@Slf4j
@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class TestSecurityConfig {

    private final AdaptiveDecisionPort adaptiveDecisionPort;
    private final AdpContextEnricher adpContextEnricher;
    private final RequestVelocityTracker velocityTracker;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/public/**").permitAll()
                        .requestMatchers("/api/health").permitAll()
                        .anyRequest().access(adpAuthorizationManager())
                )
                .httpBasic(basic -> {
                });

        return http.build();
    }

    @Bean
    public UserDetailsService userDetailsService() {
        PasswordEncoder passwordEncoder = PasswordEncoderFactories.createDelegatingPasswordEncoder();

        UserDetails user = User.builder()
                .username("user")
                .password(testPassword(passwordEncoder, "TAKIBO_ADP_TEST_USER_PASSWORD"))
                .roles("USER")
                .build();

        UserDetails admin = User.builder()
                .username("admin")
                .password(testPassword(passwordEncoder, "TAKIBO_ADP_TEST_ADMIN_PASSWORD"))
                .roles("USER", "ADMIN")
                .build();

        UserDetails suspicious = User.builder()
                .username("suspicious")
                .password(testPassword(passwordEncoder, "TAKIBO_ADP_TEST_SUSPICIOUS_PASSWORD"))
                .roles("USER")
                .build();

        return new InMemoryUserDetailsManager(user, admin, suspicious);
    }

    private String testPassword(PasswordEncoder passwordEncoder, String envName) {
        String password = System.getenv(envName);
        if (password == null || password.isBlank()) {
            password = UUID.randomUUID().toString();
        }
        return passwordEncoder.encode(password);
    }

    @Bean
    public AuthorizationManager<RequestAuthorizationContext> adpAuthorizationManager() {
        return (authSupplier, context) -> {
            Authentication auth = authSupplier.get();

            if (auth == null || !auth.isAuthenticated()) {
                log.debug("Unauthenticated request denied");
                return new AuthorizationDecision(false);
            }

            HttpServletRequest request = context.getRequest();
            String username = auth.getName();

            velocityTracker.recordRequest(username);

            Set<String> roles = auth.getAuthorities().stream()
                    .map(GrantedAuthority::getAuthority)
                    .filter(a -> a.startsWith("ROLE_"))
                    .map(a -> a.substring("ROLE_".length()))
                    .collect(Collectors.toSet());

            Set<String> permissions = auth.getAuthorities().stream()
                    .map(GrantedAuthority::getAuthority)
                    .filter(a -> !a.startsWith("ROLE_") && !a.startsWith("SCOPE_"))
                    .collect(Collectors.toSet());

            DecisionRequest decisionRequest = new DecisionRequest(
                    username,
                    "test-org",
                    "test-space",
                    roles,
                    permissions,
                    request.getRequestURI(),
                    request.getMethod(),
                    java.time.Instant.now(),
                    request.getRemoteAddr(),
                    extractDeviceFingerprint(request),
                    request.getHeader("User-Agent"),
                    null,
                    null,
                    null,
                    detectProxy(request),
                    null,
                    request.getSession(false) != null ? request.getSession(false).getId() : null,
                    velocityTracker.getVelocity(username).last10s(),
                    velocityTracker.getVelocity(username).last60s(),
                    15,
                    "1.0",
                    java.util.Map.of()
            );

            DecisionResponse response = adaptiveDecisionPort.evaluate(decisionRequest);

            log.info("ADP Decision: user={} path={} decision={} risk={} confidence={} explanation={}",
                    username,
                    request.getRequestURI(),
                    response.decision(),
                    String.format("%.1f", response.riskScore()),
                    String.format("%.2f", response.confidence()),
                    response.explanation());

            boolean granted = response.isAllowed();

            if (response.requiresChallenge()) {
                log.warn("CHALLENGE required for user={} path={}", username, request.getRequestURI());
            }

            return new AuthorizationDecision(granted);
        };
    }

    private String extractDeviceFingerprint(HttpServletRequest request) {
        String ua = request.getHeader("User-Agent");
        String accept = request.getHeader("Accept");

        if (ua == null && accept == null) {
            return "UNKNOWN";
        }

        String base = (ua != null ? ua : "") + "|" + (accept != null ? accept : "");
        return Integer.toHexString(base.hashCode());
    }

    private Boolean detectProxy(HttpServletRequest request) {
        String via = request.getHeader("Via");
        String forwarded = request.getHeader("X-Forwarded-For");
        return via != null || (forwarded != null && forwarded.split(",").length > 1);
    }
}
