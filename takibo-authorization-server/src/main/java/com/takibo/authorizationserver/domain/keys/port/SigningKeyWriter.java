package com.takibo.authorizationserver.domain.keys.port;

import com.takibo.authorizationserver.domain.keys.model.NewSigningKey;

import java.time.Instant;

/**
 * Écriture de la rotation des clés de signature de plateforme (TAS-GRANTS-02A).
 * <p>
 * Une seule opération, parce que la rotation n'a qu'une forme : activer une clé neuve et
 * programmer le retrait de celle qui signait jusque-là — jamais la supprimer, jamais changer
 * son statut à autre chose que retirée, sous peine d'invalider des JWT encore valides.
 * <p>
 * Séparé de {@link SigningKeyRepository} en lecture seule : les deux ports ne changent pas
 * pour les mêmes raisons, et confondre lecture et écriture aurait fait mentir la javadoc de
 * l'un ou de l'autre.
 */
public interface SigningKeyWriter {

    /**
     * Active {@code newKey} comme émettrice de plateforme, et retire l'ancienne s'il en
     * existait une.
     * <p>
     * « Retirer » signifie fixer sa date d'expiration à {@code retiredKeyExpiresAt} — jamais
     * la supprimer ni la révoquer. Passé ce délai, {@link SigningKeyRepository#findPublishable}
     * cesse de la servir de lui-même ; les JWT qu'elle a signés restent vérifiables jusque-là.
     * Le délai doit couvrir la durée de vie maximale d'un token que cette clé a pu signer,
     * sans quoi un JWT encore valide cesserait de se vérifier avant son expiration propre.
     * <p>
     * Sans émettrice active préalable — première activation d'une installation — rien n'est
     * retiré : {@code newKey} devient simplement l'unique émettrice.
     * <p>
     * L'unicité de l'émettrice active reste garantie par le schéma quelle que soit la
     * concurrence des appels : une activation concurrente peut échouer plutôt que de laisser
     * deux émettrices coexister, jamais l'inverse.
     */
    void activateNewIssuer(NewSigningKey newKey, Instant retiredKeyExpiresAt);
}
