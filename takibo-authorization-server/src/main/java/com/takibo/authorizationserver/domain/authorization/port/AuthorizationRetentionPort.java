package com.takibo.authorizationserver.domain.authorization.port;

import java.time.Duration;

/**
 * Retention des autorisations OAuth 2.0 expirees (TAS-GRANTS-02B).
 * <p>
 * Ce port ne connait ni horloge Java ni ordonnanceur : la selection des lignes purgeables
 * repose sur l'horloge de la base, seule commune a toutes les instances de TAKIBO. Deux
 * replicas dont les horloges JVM different de quelques secondes prendraient sinon des
 * decisions legerement differentes sur les memes lignes.
 * <p>
 * La purge est physique et definitive. Elle n'efface aucune trace d'audit : la conservation
 * de l'audit est une politique distincte, hors du perimetre de ce recit.
 */
public interface AuthorizationRetentionPort {

    /**
     * Supprime au plus {@code batchSize} autorisations dont tous les artefacts sont expires
     * depuis plus de {@code gracePeriod}.
     * <p>
     * Une seule operation SQL selectionne, verrouille et supprime, en ignorant les lignes
     * deja verrouillees par une autre instance. Aucun verrou applicatif, aucune coordination
     * externe : deux instances concurrentes se repartissent naturellement les lots.
     *
     * @param gracePeriod delai de conservation apres la derniere expiration ; operationnel,
     *                    pas OAuth — un token est invalide des son expiration, seule sa ligne
     *                    survit un moment de plus
     * @param batchSize   borne haute du lot ; aucune requete ne charge la table entiere
     * @return le nombre de lignes reellement supprimees, toujours inferieur ou egal a
     *         {@code batchSize}
     */
    int purgeOneBatch(Duration gracePeriod, int batchSize);

    /**
     * Nombre d'autorisations qu'aucune purge automatique ne supprimera jamais.
     * <p>
     * Ce sont les lignes d'echeance nulle : soit elles ne portent aucun artefact, soit elles
     * en portent un dont l'expiration est inconnue. Le purgeur ne tranche pas ces cas, il les
     * signale — une ligne assez etrange pour meriter une enquete ne l'est pas assez pour
     * qu'un travail de fond decide de la detruire.
     */
    long countUnpurgeable();
}
