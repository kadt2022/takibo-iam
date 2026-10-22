package com.takibo.iamboot.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.http.HttpHeaders;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;

import java.util.List;

/**
 * Politique CORS unique de Takibo (SEC-TMS-05), déclarée explicitement par la chaîne API
 * et par la chaîne TAS.
 */
@Configuration
@EnableConfigurationProperties(CorsProperties.class)
public class CorsConfig {

    private static final Logger log = LoggerFactory.getLogger(CorsConfig.class);

    static final List<String> ALLOWED_METHODS =
            List.of("GET", "HEAD", "POST", "PUT", "PATCH", "DELETE", "OPTIONS");

    // En-têtes que l'API lit réellement, et rien d'autre.
    static final List<String> ALLOWED_HEADERS = List.of(
            HttpHeaders.AUTHORIZATION,
            HttpHeaders.CONTENT_TYPE,
            HttpHeaders.ACCEPT,
            HttpHeaders.ACCEPT_LANGUAGE,
            "X-Request-Id",
            "X-Correlation-Id",
            "X-Client-Id",
            "X-KRYPTION-ID");

    // Location : ressources créées. X-KRYPTION-ID et X-Trace-Id : corrélation des erreurs.
    // X-Client-Ip n'est pas exposé : il rendrait au script l'adresse vue par le serveur.
    static final List<String> EXPOSED_HEADERS =
            List.of(HttpHeaders.LOCATION, "X-KRYPTION-ID", "X-Trace-Id");

    @Bean
    public CorsConfigurationSource corsConfigurationSource(CorsProperties properties, Environment environment) {
        boolean devProfile = environment.acceptsProfiles(Profiles.of("dev"));
        CorsAllowedOrigins origins = CorsAllowedOrigins.validate(properties.getAllowedOrigins(), devProfile);

        if (origins.isEmpty()) {
            log.info("CORS : aucune origine croisée acceptée ({} vide).", CorsAllowedOrigins.PROPERTY);
        } else {
            log.info("CORS : origines croisées acceptées {} ; motifs du profil dev {}.",
                    origins.exactOrigins(), origins.originPatterns());
        }

        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(origins.exactOrigins());
        configuration.setAllowedOriginPatterns(origins.originPatterns());
        configuration.setAllowedMethods(ALLOWED_METHODS);
        configuration.setAllowedHeaders(ALLOWED_HEADERS);
        configuration.setExposedHeaders(EXPOSED_HEADERS);
        // Sans interrupteur, dans tous les profils : l'API s'authentifie par l'en-tête
        // Authorization, que le frontend pose lui-même. Aucun cookie n'a à traverser CORS.
        configuration.setAllowCredentials(false);

        // Volontairement pas un UrlBasedCorsConfigurationSource. Spring Security applique
        // d'office cors() à toute chaîne dès qu'un bean de ce type existe
        // (HttpSecurityConfiguration.applyCorsIfAvailable), selon l'ordre d'instanciation des
        // beans. Ici, une chaîne n'a de CORS que si elle le déclare.
        return request -> configuration;
    }
}
