package com.takibo.authorizationserver.application;

import com.takibo.authorizationserver.domain.authorization.port.AuthorizationRetentionPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;

/**
 * Politique de retention des autorisations OAuth 2.0 (TAS-GRANTS-02B).
 * <p>
 * Un passage enchaine des lots bornes jusqu'a ce qu'il n'y ait plus rien a supprimer, ou
 * jusqu'a la limite de lots du passage. Cette limite existe pour qu'un retard accumule ne
 * transforme pas un tic en travail sans fin : le passage suivant reprendra ou celui-ci
 * s'est arrete.
 * <p>
 * Le service est idempotent par construction. Sur une base deja purgee, le premier lot
 * revient vide et le passage s'arrete sans rien ecrire.
 */
public class AuthorizationRetentionService {

    private static final Logger log = LoggerFactory.getLogger(AuthorizationRetentionService.class);

    private final AuthorizationRetentionPort retention;
    private final Duration gracePeriod;
    private final int batchSize;
    private final int maxBatchesPerRun;

    public AuthorizationRetentionService(AuthorizationRetentionPort retention,
                                         Duration gracePeriod,
                                         int batchSize,
                                         int maxBatchesPerRun) {
        this.retention = retention;
        this.gracePeriod = gracePeriod;
        this.batchSize = batchSize;
        this.maxBatchesPerRun = maxBatchesPerRun;
    }

    /**
     * Un passage complet de purge.
     *
     * @return le nombre total d'autorisations supprimees pendant ce passage
     */
    public int purge() {
        int deleted = 0;
        for (int batch = 0; batch < maxBatchesPerRun; batch++) {
            int removed = retention.purgeOneBatch(gracePeriod, batchSize);
            deleted += removed;
            // Un lot incomplet signifie qu'il ne reste plus rien a prendre : soit la table
            // est a jour, soit une autre instance tient le reste. Dans les deux cas, insister
            // ne servirait qu'a repasser sur des lignes verrouillees.
            if (removed < batchSize) {
                break;
            }
        }

        if (deleted > 0) {
            log.info("TAS retention: {} autorisation(s) expiree(s) supprimee(s)", deleted);
        }

        reportAnomalies();
        return deleted;
    }

    /**
     * Signalement agrege, jamais ligne par ligne : une anomalie repetee a chaque passage
     * noierait le journal sans rien apprendre de plus qu'un compteur.
     */
    private void reportAnomalies() {
        long unpurgeable = retention.countUnpurgeable();
        if (unpurgeable > 0) {
            log.warn("TAS retention: {} autorisation(s) sans echeance exploitable — jamais "
                    + "purgees automatiquement, a investiguer", unpurgeable);
        }
    }
}
