package com.takibo.authorizationserver.infrastructure.springauthserver.retention;

import com.takibo.authorizationserver.application.AuthorizationRetentionService;
import com.takibo.authorizationserver.domain.authorization.port.AuthorizationRetentionPort;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Cablage de la retention (TAS-GRANTS-02B).
 * <p>
 * Le point verifie n'est pas que les beans existent, c'est <b>lesquels</b> disparaissent
 * quand la retention est desactivee. Le port et le service restent declares, pour qu'un test
 * ou un declenchement manuel puisse encore purger ; seul l'ordonnanceur s'efface. Une
 * instance peut ainsi servir le trafic sans participer au travail de fond.
 */
class AuthorizationRetentionConfigurationTest {

    /**
     * Le {@code JdbcTemplate} est fourni directement plutot que par auto-configuration : ce
     * test porte sur le cablage de la retention, pas sur la resolution d'une source de
     * donnees. Aucune requete n'est emise, seuls les beans sont assembles.
     */
    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withBean(JdbcTemplate.class, () -> new JdbcTemplate(org.mockito.Mockito.mock(javax.sql.DataSource.class)))
            .withUserConfiguration(AuthorizationRetentionConfiguration.class);

    @Test
    void given_no_configuration_then_the_retention_runs_with_its_defaults() {
        runner.run(context -> {
            assertThat(context).hasSingleBean(AuthorizationRetentionPort.class);
            assertThat(context).hasSingleBean(AuthorizationRetentionService.class);
            assertThat(context)
                    .as("la purge est active par defaut : une base qui grossit sans fin est un "
                            + "defaut silencieux")
                    .hasSingleBean(AuthorizationRetentionScheduler.class);
        });
    }

    @Test
    void given_the_retention_disabled_then_only_the_scheduler_disappears() {
        runner.withPropertyValues("takibo.tas.retention.enabled=false").run(context -> {
            assertThat(context).doesNotHaveBean(AuthorizationRetentionScheduler.class);
            assertThat(context)
                    .as("le port et le service restent appelables hors ordonnancement")
                    .hasSingleBean(AuthorizationRetentionPort.class);
            assertThat(context).hasSingleBean(AuthorizationRetentionService.class);
        });
    }

    @Test
    void given_absurd_settings_then_the_context_refuses_to_start() {
        runner.withPropertyValues("takibo.tas.retention.batch-size=0").run(context ->
                assertThat(context)
                        .as("une configuration invalide doit se voir au demarrage, pas produire "
                                + "une purge silencieusement inerte")
                        .hasFailed());
    }
}
