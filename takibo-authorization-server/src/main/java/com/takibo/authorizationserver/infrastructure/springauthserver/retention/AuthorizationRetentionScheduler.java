package com.takibo.authorizationserver.infrastructure.springauthserver.retention;

import com.takibo.authorizationserver.application.AuthorizationRetentionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;

/**
 * Declencheur periodique de la purge (TAS-GRANTS-02B).
 * <p>
 * {@code fixedDelay} et non {@code fixedRate} : le delai court apres la fin du passage
 * precedent, donc un passage long ne fait jamais s'empiler les suivants dans la meme
 * instance. Entre instances, c'est {@code SKIP LOCKED} qui repartit le travail.
 * <p>
 * Un echec ne remonte pas : l'ordonnanceur Spring arreterait definitivement la tache
 * planifiee sur une exception. Une base momentanement injoignable ne doit pas eteindre la
 * retention jusqu'au prochain redemarrage.
 */
public class AuthorizationRetentionScheduler {

    private static final Logger log = LoggerFactory.getLogger(AuthorizationRetentionScheduler.class);

    private final AuthorizationRetentionService retention;

    public AuthorizationRetentionScheduler(AuthorizationRetentionService retention) {
        this.retention = retention;
    }

    @Scheduled(
            fixedDelayString = "${takibo.tas.retention.interval:1h}",
            initialDelayString = "${takibo.tas.retention.initial-delay:5m}"
    )
    public void tick() {
        try {
            retention.purge();
        } catch (RuntimeException e) {
            log.error("TAS retention: passage en echec, reprise au prochain intervalle", e);
        }
    }
}
