package com.takibo.authorizationserver.infrastructure.springauthserver.retention;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Configuration de la retention des autorisations OAuth 2.0 (TAS-GRANTS-02B).
 * <p>
 * Volontairement generique : un intervalle, une taille de lot, un delai de grace. Aucune
 * supposition sur l'orchestrateur de deploiement, aucune notion de pod, de cron externe ni
 * de plateforme. TAKIBO exprime son besoin, l'installateur decide de la valeur.
 *
 * @param enabled          la purge tourne-t-elle dans cette instance ; activee par defaut,
 *                         parce qu'une base qui grossit sans fin est un defaut silencieux
 * @param gracePeriod      duree de conservation apres la derniere expiration. Operationnel,
 *                         pas OAuth : un token est invalide des son expiration, seule sa
 *                         ligne survit un moment de plus. Utile pour qu'un incident laisse
 *                         encore quelque chose a inspecter
 * @param batchSize        borne haute d'un lot ; aucune requete ne charge la table entiere
 * @param maxBatchesPerRun bornes du passage entier, pour qu'un retard accumule ne
 *                         transforme pas un tic en travail sans fin
 */
@ConfigurationProperties(prefix = "takibo.tas.retention")
public record AuthorizationRetentionProperties(
        Boolean enabled,
        Duration gracePeriod,
        Integer batchSize,
        Integer maxBatchesPerRun
) {

    private static final Duration DEFAULT_GRACE_PERIOD = Duration.ofHours(24);
    private static final int DEFAULT_BATCH_SIZE = 500;
    private static final int DEFAULT_MAX_BATCHES_PER_RUN = 20;

    public AuthorizationRetentionProperties {
        enabled = enabled == null || enabled;
        gracePeriod = gracePeriod == null ? DEFAULT_GRACE_PERIOD : gracePeriod;
        batchSize = batchSize == null ? DEFAULT_BATCH_SIZE : batchSize;
        maxBatchesPerRun = maxBatchesPerRun == null ? DEFAULT_MAX_BATCHES_PER_RUN : maxBatchesPerRun;

        if (gracePeriod.isNegative()) {
            throw new IllegalArgumentException("TAS_RETENTION_GRACE_PERIOD_MUST_NOT_BE_NEGATIVE");
        }
        if (batchSize <= 0) {
            throw new IllegalArgumentException("TAS_RETENTION_BATCH_SIZE_MUST_BE_POSITIVE");
        }
        if (maxBatchesPerRun <= 0) {
            throw new IllegalArgumentException("TAS_RETENTION_MAX_BATCHES_MUST_BE_POSITIVE");
        }
    }
}
