package com.takibo.identitycore.domain.model;

/**
 * SEC-HUNT-02 — une tentative de mot de passe réservée avant sa vérification.
 * <p>
 * La réservation compte la tentative comme un échec <b>avant</b> que le mot de passe soit
 * comparé : sous des requêtes concurrentes, au plus {@code maxFailedAttempts} comparaisons ont
 * lieu avant le verrou. Une tentative qui se révèle juste est ensuite soldée (succès) ou
 * annulée (refus sans rapport avec le mot de passe).
 *
 * @param failedAttempts compteur d'échecs après la réservation
 * @param locksAccount   vrai si cette réservation a posé le verrou
 */
public record FailedAttemptReservation(int failedAttempts, boolean locksAccount) {
}
