package com.takibo.identitycore.domain.repository;


import com.takibo.identitycore.domain.model.AccountCredentials;
import com.takibo.identitycore.domain.model.FailedAttemptReservation;
import com.takibo.identitycore.domain.vo.AccountId;
import com.takibo.identitycore.domain.vo.OrganizationId;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;


public interface AccountCredentialsRepository {
    AccountCredentials save(AccountCredentials credentials, UUID orgId);

    Optional<AccountCredentials> find(OrganizationId orgId, AccountId accountId);

    /**
     * SEC-HUNT-02 : compte atomiquement une tentative comme un échec, avant toute vérification du
     * mot de passe, et pose le verrou si le seuil est atteint. Vide si le compte est verrouillé :
     * la tentative n'est alors ni comptée ni vérifiée.
     *
     * @param lockUntil fin du verrou à poser au seuil ; {@code null} : jamais de verrou
     */
    Optional<FailedAttemptReservation> reserveFailedAttempt(OrganizationId orgId, AccountId accountId,
                                                            int maxFailedAttempts, Instant lockUntil, Instant now);

    /**
     * Annule une réservation : le compteur perd la tentative, et le verrou est levé si cette
     * réservation l'avait posé ou si le compteur repasse sous le seuil (un échec concurrent a pu
     * poser le verrou entre la réservation et son annulation).
     */
    void cancelFailedAttempt(OrganizationId orgId, AccountId accountId, FailedAttemptReservation reservation,
                             int maxFailedAttempts, Instant now);

    /** Succès : remet le compteur à zéro et lève le verrou, sans conflit de version. */
    void clearFailedAttempts(OrganizationId orgId, AccountId accountId, Instant now);
}
