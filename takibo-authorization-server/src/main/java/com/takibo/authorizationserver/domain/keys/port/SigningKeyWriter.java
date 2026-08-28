package com.takibo.authorizationserver.domain.keys.port;

import com.takibo.authorizationserver.domain.keys.model.NewSigningKey;

import java.time.Instant;

/**
 * Écriture de la rotation des clés de signature de plateforme (TAS-GRANTS-02A).
 * <p>
 * Deux opérations, pas une : l'amorçage d'une installation sans émettrice et la rotation
 * d'une émettrice existante ne sont pas la même situation, et les confondre a un coût réel —
 * une rotation qui trouve la table vide silencieusement transformée en amorçage, ou un
 * amorçage qui retirerait une émettrice déjà là sans que rien ne le signale. Chacune des deux
 * méthodes ci-dessous n'est sûre que dans le cas qu'elle nomme ; l'appelant — ici
 * {@code SigningKeyRotationService} — choisit laquelle selon ce qu'il sait de la situation, et
 * chacune fait échouer l'autre cas plutôt que de le traiter en silence.
 * <p>
 * Séparé de {@link SigningKeyRepository} en lecture seule : les deux ports ne changent pas
 * pour les mêmes raisons, et confondre lecture et écriture aurait fait mentir la javadoc de
 * l'un ou de l'autre.
 */
public interface SigningKeyWriter {

    /**
     * Active {@code newKey} comme unique émettrice de plateforme, sans rien retirer.
     * <p>
     * Réservé à l'installation d'une plateforme qui n'a encore aucune émettrice. S'il en
     * existe déjà une, l'écriture doit échouer plutôt que d'en laisser deux actives à la
     * fois — l'implémentation s'appuie sur l'index unique partiel du schéma pour cette
     * garantie, pas sur une vérification préalable qui laisserait une fenêtre de course.
     */
    void activateFirstIssuer(NewSigningKey newKey);

    /**
     * Active {@code newKey} comme émettrice de plateforme et retire celle qui l'était jusque
     * là.
     * <p>
     * Réservé à la rotation d'une installation qui a déjà une émettrice active ; sans cela,
     * il n'y a rien à retirer et l'appel doit échouer plutôt que de se comporter comme un
     * amorçage silencieux — {@link #activateFirstIssuer} existe précisément pour ce cas.
     * <p>
     * « Retirer » signifie fixer sa date de fin de publication à {@code retiredKeyExpiresAt}
     * — jamais la supprimer ni la révoquer. Passé ce délai,
     * {@link SigningKeyRepository#findPublishable} cesse de la servir de lui-même ; les JWT
     * qu'elle a signés restent vérifiables jusque-là. Le délai doit couvrir la durée de vie
     * maximale d'un token que cette clé a pu signer, sans quoi un JWT encore valide cesserait
     * de se vérifier avant son expiration propre — c'est pourquoi l'appelant doit fournir un
     * délai strictement positif.
     * <p>
     * L'unicité de l'émettrice active reste garantie par le schéma quelle que soit la
     * concurrence des appels : une activation concurrente peut échouer plutôt que de laisser
     * deux émettrices coexister, jamais l'inverse.
     */
    void activateNewIssuer(NewSigningKey newKey, Instant retiredKeyExpiresAt);
}
