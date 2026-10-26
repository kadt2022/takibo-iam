package com.takibo.iamboot.ui;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.OrRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;

import org.springframework.web.cors.CorsConfigurationSource;

import java.util.stream.Stream;

/**
 * Service de TAKIBO UI (build Vite, embarqué sous {@code static/} par {@code bootJar}).
 *
 * <p>La chaîne de sécurité API reste inchangée et fail-closed ({@code anyRequest().denyAll()}).
 * Cette chaîne dédiée n'ouvre, en lecture seule (GET/HEAD), que la liste fermée ci-dessous : les
 * fichiers de l'interface et ses routes. Tout le reste, {@code /api/**} et {@code /actuator/**}
 * compris, retombe sur les chaînes existantes. Une nouvelle route de {@code router.tsx} doit être
 * ajoutée ici et dans UiRoutesController pour survivre à un rafraîchissement.
 */
@Configuration
public class UiConfig {

    /** Routes de l'application monopage, servies par {@code index.html} (à garder alignées sur UiRoutesController). */
    private static final String[] ROUTES = {"/login", "/app", "/app/**"};

    /** Fichiers du build ({@code takibo-ui/dist}) ; {@code /} est la page d'accueil. */
    private static final String[] FILES = {"/", "/index.html", "/assets/**", "/favicon.svg", "/takibo-logo.png"};

    // Après la chaîne du serveur d'autorisation (@Order(1)), avant la chaîne API (ordre le plus bas).
    @Bean
    @Order(2)
    public SecurityFilterChain uiSecurityFilterChain(HttpSecurity http,
                                                     CorsConfigurationSource corsConfigurationSource) throws Exception {
        http
                // SEC-TMS-05 : chaque chaîne déclare sa source CORS, y compris celle-ci.
                .cors(cors -> cors.configurationSource(corsConfigurationSource))
                .securityMatcher(uiRequests())
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll());
        return http.build();
    }

    private static RequestMatcher uiRequests() {
        PathPatternRequestMatcher.Builder paths = PathPatternRequestMatcher.withDefaults();
        return new OrRequestMatcher(Stream.concat(Stream.of(FILES), Stream.of(ROUTES))
                .flatMap(pattern -> Stream.of(HttpMethod.GET, HttpMethod.HEAD)
                        .<RequestMatcher>map(method -> paths.matcher(method, pattern)))
                .toList());
    }
}
