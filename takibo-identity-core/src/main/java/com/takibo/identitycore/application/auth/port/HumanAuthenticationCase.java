package com.takibo.identitycore.application.auth.port;

import com.takibo.identitycore.application.auth.model.HumanCredentials;
import com.takibo.identitycore.application.auth.model.TrustedOrganizationContext;
import com.takibo.identitycore.application.auth.model.VerifiedHuman;

/**
 * Seule vérification des credentials humains de TAKIBO (TAS-GRANTS-03-A), partagée par le
 * login REST et la future session navigateur TAS. Elle authentifie un Account dans une
 * Organization déjà résolue, et nulle part ailleurs ; elle ne signe rien et ne crée aucune
 * session.
 */
public interface HumanAuthenticationCase {

    /**
     * @throws com.takibo.identitycore.domain.exception.AuthenticationFailedException pour toute
     *         cause d'échec, sans la révéler : compte inconnu dans l'Organization, credentials
     *         absents, compte verrouillé, mauvais mot de passe
     */
    VerifiedHuman authenticate(TrustedOrganizationContext context, HumanCredentials credentials);

    /**
     * Paie le coût d'une vérification de mot de passe quand aucun contexte fiable n'a pu être
     * établi (Organization inconnue ou inactive), pour que le temps de réponse ne trahisse pas
     * la cause de l'échec.
     */
    void equalizeFailedAttempt(HumanCredentials credentials);
}
