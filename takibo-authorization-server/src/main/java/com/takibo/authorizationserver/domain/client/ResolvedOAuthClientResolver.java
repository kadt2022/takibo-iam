package com.takibo.authorizationserver.domain.client;

import java.util.Optional;

/**
 * Port de resolution d'un client OAuth2, par son {@code client_id} public et rien d'autre.
 * <p>
 * Le TAS ne connait ni la source des clients ni sa forme : le registre appartient au
 * management-service, et l'adaptateur qui traduit son vocabulaire vers
 * {@link ResolvedOAuthClient} est cable dans {@code takibo-iam-boot}. Aucun des deux modules
 * ne depend de l'autre.
 * <p>
 * La resolution est <b>globale</b> : {@code client_id} est unique a l'echelle de
 * l'installation, et c'est precisement ce qui rend la frontiere resoluble sans la connaitre
 * au prealable. Les surfaces qui n'ont pas de tenant sous la main — la verification d'un
 * device par {@code user_code}, l'application de PKCE avant toute authentification — en
 * dependent directement.
 * <p>
 * Contrat fail-closed : un client inconnu, desactive ou incoherent donne un
 * {@link Optional#empty()}. Ce port ne fabrique jamais de client par defaut.
 */
public interface ResolvedOAuthClientResolver {

    Optional<ResolvedOAuthClient> resolve(String clientId);
}
