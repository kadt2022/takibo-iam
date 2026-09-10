package com.takibo.authorizationserver.infrastructure.springauthserver.retention;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Configuration de la retention (TAS-GRANTS-02B).
 * <p>
 * Le critere demande une configuration <b>generique</b>, qui fonctionne sans supposition sur
 * l'environnement d'execution. Ces tests verifient donc deux choses : qu'une installation
 * qui ne configure rien obtient des valeurs de travail, et qu'une valeur absurde est
 * refusee au demarrage plutot que traduite en comportement surprenant.
 */
class AuthorizationRetentionPropertiesTest {

    @Test
    void given_no_configuration_at_all_then_working_defaults_apply() {
        AuthorizationRetentionProperties properties =
                new AuthorizationRetentionProperties(null, null, null, null);

        assertThat(properties.enabled()).isTrue();
        assertThat(properties.gracePeriod()).isEqualTo(Duration.ofHours(24));
        assertThat(properties.batchSize()).isEqualTo(500);
        assertThat(properties.maxBatchesPerRun()).isEqualTo(20);
    }

    @Test
    void given_explicit_values_then_they_win_over_the_defaults() {
        AuthorizationRetentionProperties properties = new AuthorizationRetentionProperties(
                false, Duration.ofMinutes(30), 50, 2);

        assertThat(properties.enabled()).isFalse();
        assertThat(properties.gracePeriod()).isEqualTo(Duration.ofMinutes(30));
        assertThat(properties.batchSize()).isEqualTo(50);
        assertThat(properties.maxBatchesPerRun()).isEqualTo(2);
    }

    @Test
    void given_a_zero_grace_period_then_it_is_accepted() {
        // Purger des l'expiration est un choix legitime, pas une erreur de saisie.
        assertThat(new AuthorizationRetentionProperties(null, Duration.ZERO, null, null).gracePeriod())
                .isEqualTo(Duration.ZERO);
    }

    @Test
    void given_absurd_values_then_the_context_refuses_to_start() {
        assertThatThrownBy(() ->
                new AuthorizationRetentionProperties(null, Duration.ofMinutes(-1), null, null))
                .hasMessageContaining("TAS_RETENTION_GRACE_PERIOD_MUST_NOT_BE_NEGATIVE");

        assertThatThrownBy(() -> new AuthorizationRetentionProperties(null, null, 0, null))
                .hasMessageContaining("TAS_RETENTION_BATCH_SIZE_MUST_BE_POSITIVE");

        assertThatThrownBy(() -> new AuthorizationRetentionProperties(null, null, null, 0))
                .hasMessageContaining("TAS_RETENTION_MAX_BATCHES_MUST_BE_POSITIVE");
    }
}
