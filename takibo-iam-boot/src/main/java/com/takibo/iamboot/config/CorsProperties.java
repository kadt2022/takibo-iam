package com.takibo.iamboot.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

/**
 * Contrat d'installation CORS (SEC-TMS-05). Les valeurs brutes sont validées par
 * {@link CorsAllowedOrigins} au moment de construire la politique.
 */
@ConfigurationProperties(prefix = "takibo.cors")
public class CorsProperties {

    private List<String> allowedOrigins = new ArrayList<>();

    public List<String> getAllowedOrigins() {
        return allowedOrigins;
    }

    public void setAllowedOrigins(List<String> allowedOrigins) {
        this.allowedOrigins = allowedOrigins;
    }
}
