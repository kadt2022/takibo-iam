package com.takibo.authorizationserver.infrastructure.springauthserver.retention;

import com.takibo.authorizationserver.application.AuthorizationRetentionService;
import com.takibo.authorizationserver.domain.authorization.port.AuthorizationRetentionPort;
import com.takibo.authorizationserver.infrastructure.jpa.repository.JdbcAuthorizationRetentionAdapter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Cablage de la retention des autorisations OAuth 2.0 (TAS-GRANTS-02B).
 * <p>
 * Le port et le service sont toujours declares : ils restent appelables par un test ou par
 * un futur declencheur manuel. Seul l'ordonnanceur est conditionne, pour qu'une instance
 * puisse participer au service sans participer a la purge.
 */
@Configuration
@EnableScheduling
@EnableConfigurationProperties(AuthorizationRetentionProperties.class)
public class AuthorizationRetentionConfiguration {

    @Bean
    public AuthorizationRetentionPort authorizationRetentionPort(JdbcTemplate jdbcTemplate) {
        return new JdbcAuthorizationRetentionAdapter(jdbcTemplate);
    }

    @Bean
    public AuthorizationRetentionService authorizationRetentionService(
            AuthorizationRetentionPort retentionPort,
            AuthorizationRetentionProperties properties) {

        return new AuthorizationRetentionService(
                retentionPort,
                properties.gracePeriod(),
                properties.batchSize(),
                properties.maxBatchesPerRun());
    }

    @Bean
    @ConditionalOnProperty(name = "takibo.tas.retention.enabled", havingValue = "true", matchIfMissing = true)
    public AuthorizationRetentionScheduler authorizationRetentionScheduler(
            AuthorizationRetentionService retentionService) {

        return new AuthorizationRetentionScheduler(retentionService);
    }
}
