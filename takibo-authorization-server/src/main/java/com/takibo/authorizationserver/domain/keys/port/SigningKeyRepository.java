package com.takibo.authorizationserver.domain.keys.port;

import com.takibo.authorizationserver.domain.keys.model.TasSigningKey;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Acces en lecture aux cles de signature de la plateforme (TAS-GRANTS-02A).
 * <p>
 * Deux questions, et deux seulement, parce qu'elles correspondent aux deux usages reels :
 * quelle cle signe maintenant, et quelles cles doivent encore etre publiees pour que les JWT
 * deja emis restent verifiables.
 * <p>
 * La distinction est le coeur de la rotation. Une cle retiree ne signe plus mais doit rester
 * verifiable jusqu'a l'expiration du dernier token qu'elle a signe ; une cle revoquee ne
 * verifie plus rien. Confondre les deux, c'est soit invalider des tokens valides, soit
 * continuer d'accepter ceux d'une cle compromise.
 * <p>
 * L'instant est un parametre, jamais lu de l'horloge par l'implementation : c'est ce qui rend
 * les fenetres {@code not_before}, {@code expires_at} et {@code publish_until} verifiables
 * par test.
 */
public interface SigningKeyRepository {

    /**
     * La cle qui signe : emettrice, active, temporellement valide, et de portee plateforme.
     * <p>
     * Le schema garantit qu'il n'y en a jamais plus d'une — index partiel
     * {@code uk_tas_sk_platform_issuer_active}. Son absence n'est pas une situation
     * degradee mais une erreur de configuration : TAS ne peut rien emettre.
     */
    Optional<TasSigningKey> findActivePlatformIssuer(Instant at);

    /**
     * Les cles a publier dans le JWKS : actives et retirees, temporellement valides, jamais
     * revoquees. Inclut l'emettrice.
     */
    List<TasSigningKey> findPublishable(Instant at);

    /**
     * Existe-t-il la moindre cle de plateforme, quel que soit son statut ?
     * <p>
     * Question distincte de {@link #findActivePlatformIssuer} : c'est elle qui separe une
     * installation vierge — legitimement amorcable — d'une installation dont l'histoire des
     * cles existe mais n'a plus d'emettrice active. Le second cas denonce une corruption, une
     * restauration partielle ou une rotation interrompue, et amorcer une cle neuve par-dessus
     * le masquerait : TAS signerait avec une cle que rien n'a decidee, a cote d'un historique
     * que personne n'aurait examine.
     * <p>
     * Aucune borne temporelle, contrairement aux deux lectures ci-dessus : une cle expiree ou
     * revoquee reste une trace d'histoire, et c'est precisement ce que cette question cherche.
     */
    boolean hasPlatformKeyHistory();
}
