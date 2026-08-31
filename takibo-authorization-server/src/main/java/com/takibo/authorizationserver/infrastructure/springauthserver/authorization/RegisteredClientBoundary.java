package com.takibo.authorizationserver.infrastructure.springauthserver.authorization;

import com.takibo.authorizationserver.infrastructure.springauthserver.token.TakiboTokenClaims;
import org.springframework.dao.DataRetrievalFailureException;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.util.StringUtils;

import java.util.Objects;
import java.util.UUID;

/**
 * Résolution et vérification de frontière partagées entre {@link JpaOAuth2AuthorizationService}
 * et {@link JpaOAuth2AuthorizationConsentService} (TAS-GRANTS-02) : les deux dérivent
 * {@code org_id}/{@code space_id} du {@link RegisteredClient} résolu et doivent refuser
 * identiquement un client devenu irrésolvable, ou déplacé sous un autre tenant depuis
 * l'écriture — extrait ici pour ne pas garder deux copies à faire évoluer en parallèle
 * (SonarCloud, duplication sur ces deux classes).
 */
final class RegisteredClientBoundary {

    private RegisteredClientBoundary() {
    }

    static RegisteredClient requireResolvableClient(
            RegisteredClientRepository registeredClientRepository, String registeredClientId) {
        RegisteredClient client = registeredClientRepository.findById(registeredClientId);
        if (client == null) {
            // Ecrit noir sur blanc l'exigence de TAS-GRANTS-02 : une autorisation ou un
            // consentement ne peuvent se reconstruire que pour un client encore resolvable par
            // ResolvedOAuthClientResolver (via TakiboRegisteredClientRepository) -- jamais
            // silencieusement.
            throw new DataRetrievalFailureException(
                    "The RegisteredClient with id '" + registeredClientId + "' was not found");
        }
        return client;
    }

    /**
     * Refuse de reconstruire une autorisation ou un consentement dont la frontière a divergé de
     * celle du client résolu <b>maintenant</b>. Sans ce contrôle, un client déplacé ou recréé
     * sous une autre organisation/space entre l'émission et la relecture ferait rejouer un
     * refresh token, ou sauter l'écran de consentement, sous le nouveau tenant :
     * {@code TakiboOAuth2TokenCustomizer} lit {@code org_id}/{@code space_id} depuis le
     * {@link RegisteredClient} résolu à l'instant présent, jamais depuis la ligne elle-même, qui
     * ne les porte pas de façon indépendante. Échoue fermé plutôt que de laisser un token ou un
     * consentement franchir silencieusement une frontière de tenant.
     *
     * @param recordKind {@code "authorization"} ou {@code "consent"} — uniquement pour le
     *                   message d'erreur
     */
    static void requireMatchingBoundary(
            String registeredClientId, UUID savedOrgId, UUID savedSpaceId, RegisteredClient client,
            String recordKind) {
        UUID currentOrgId = readUuidSetting(client, TakiboTokenClaims.ORG_ID);
        UUID currentSpaceId = readUuidSetting(client, TakiboTokenClaims.SPACE_ID);
        if (!Objects.equals(savedOrgId, currentOrgId) || !Objects.equals(savedSpaceId, currentSpaceId)) {
            throw new DataRetrievalFailureException(
                    "The RegisteredClient with id '" + registeredClientId
                            + "' no longer resolves under the org/space this " + recordKind
                            + " was saved for");
        }
    }

    static UUID readUuidSetting(RegisteredClient client, String settingName) {
        String value = client.getClientSettings().getSetting(settingName);
        return StringUtils.hasText(value) ? UUID.fromString(value) : null;
    }
}
