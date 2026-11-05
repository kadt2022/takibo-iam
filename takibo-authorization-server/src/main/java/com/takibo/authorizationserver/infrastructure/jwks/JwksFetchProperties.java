package com.takibo.authorizationserver.infrastructure.jwks;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.unit.DataSize;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * Contrat d'installation de la récupération des JWK Sets distants. Valeurs par défaut sûres :
 * aucune destination interne, HTTPS seulement.
 */
@ConfigurationProperties(prefix = "takibo.oauth.jwks-fetch")
public class JwksFetchProperties {

    /** Noms d'hôtes exacts autorisés à se résoudre vers une adresse privée ou locale. */
    private List<String> trustedHosts = new ArrayList<>();

    /** Plages autorisées pour n'importe quel nom ; vides par défaut. */
    private List<String> trustedCidrs = new ArrayList<>();

    /** HTTP vers un nom de confiance : profil dev uniquement, sinon le démarrage échoue. */
    private boolean allowHttpForTrustedHosts;

    private Duration connectTimeout = Duration.ofSeconds(2);
    private Duration readTimeout = Duration.ofSeconds(3);
    private Duration totalTimeout = Duration.ofSeconds(5);
    private DataSize maxResponseSize = DataSize.ofKilobytes(50);

    public List<String> getTrustedHosts() {
        return trustedHosts;
    }

    public void setTrustedHosts(List<String> trustedHosts) {
        this.trustedHosts = trustedHosts;
    }

    public List<String> getTrustedCidrs() {
        return trustedCidrs;
    }

    public void setTrustedCidrs(List<String> trustedCidrs) {
        this.trustedCidrs = trustedCidrs;
    }

    public boolean isAllowHttpForTrustedHosts() {
        return allowHttpForTrustedHosts;
    }

    public void setAllowHttpForTrustedHosts(boolean allowHttpForTrustedHosts) {
        this.allowHttpForTrustedHosts = allowHttpForTrustedHosts;
    }

    public Duration getConnectTimeout() {
        return connectTimeout;
    }

    public void setConnectTimeout(Duration connectTimeout) {
        this.connectTimeout = connectTimeout;
    }

    public Duration getReadTimeout() {
        return readTimeout;
    }

    public void setReadTimeout(Duration readTimeout) {
        this.readTimeout = readTimeout;
    }

    public Duration getTotalTimeout() {
        return totalTimeout;
    }

    public void setTotalTimeout(Duration totalTimeout) {
        this.totalTimeout = totalTimeout;
    }

    public DataSize getMaxResponseSize() {
        return maxResponseSize;
    }

    public void setMaxResponseSize(DataSize maxResponseSize) {
        this.maxResponseSize = maxResponseSize;
    }
}
