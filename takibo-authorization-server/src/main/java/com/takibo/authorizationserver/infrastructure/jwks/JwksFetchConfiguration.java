package com.takibo.authorizationserver.infrastructure.jwks;

import org.apache.hc.core5.ssl.SSLContexts;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;

import java.time.Duration;

/**
 * Assemble la politique et le client JWKS. Une configuration dangereuse fait échouer le
 * démarrage au lieu d'être ignorée en silence.
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(JwksFetchProperties.class)
public class JwksFetchConfiguration {

    static final String DEV_PROFILE = "dev";

    private static final Logger log = LoggerFactory.getLogger(JwksFetchConfiguration.class);

    @Bean
    OutboundDestinationPolicy jwksDestinationPolicy(JwksFetchProperties properties, Environment environment) {
        if (properties.isAllowHttpForTrustedHosts() && !environment.acceptsProfiles(Profiles.of(DEV_PROFILE))) {
            throw new IllegalStateException("takibo.oauth.jwks-fetch.allow-http-for-trusted-hosts is reserved to the "
                    + "'dev' profile; remove it or activate the profile explicitly");
        }
        OutboundDestinationPolicy policy = new OutboundDestinationPolicy(
                properties.getTrustedHosts(), properties.getTrustedCidrs(), properties.isAllowHttpForTrustedHosts());
        policy.broadTrustedCidrs().forEach(cidr -> log.warn(
                "JWKS : plage de confiance large {} ; toute adresse qu'elle contient devient joignable.", cidr));
        if (policy.httpAllowedForTrustedHosts()) {
            log.warn("JWKS : HTTP accepté vers les noms de confiance {} (profil dev).", properties.getTrustedHosts());
        }
        return policy;
    }

    @Bean
    JwksFetchClient jwksFetchClient(OutboundDestinationPolicy jwksDestinationPolicy, JwksFetchProperties properties) {
        JwksFetchClient.Limits limits = new JwksFetchClient.Limits(
                requirePositive(properties.getConnectTimeout(), "connect-timeout"),
                requirePositive(properties.getReadTimeout(), "read-timeout"),
                requirePositive(properties.getTotalTimeout(), "total-timeout"),
                properties.getMaxResponseSize().toBytes());
        if (limits.maxResponseBytes() <= 0) {
            throw new IllegalStateException("takibo.oauth.jwks-fetch.max-response-size must be positive");
        }
        return new JwksFetchClient(jwksDestinationPolicy, limits, SSLContexts.createSystemDefault());
    }

    private static Duration requirePositive(Duration value, String name) {
        if (value == null || value.isZero() || value.isNegative()) {
            throw new IllegalStateException("takibo.oauth.jwks-fetch." + name + " must be positive");
        }
        return value;
    }
}
