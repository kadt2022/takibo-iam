package com.takibo.managementservice.application.query.port;

import java.util.UUID;

/**
 * Port de lecture des clients OAuth2 d'une organisation, au service du read-side
 * dashboard. La couche application définit le contrat ; l'implémentation vit dans
 * l'infrastructure (JPA). Aucun secret n'est exposé par ce port : uniquement des
 * compteurs. Le management situé (création/rotation) reste sur sa propre surface.
 */
public interface OAuthClientQueryCase {

    /**
     * Compteur direct de tous les clients OAuth2 persistés dans les Spaces de
     * l'organisation — aucune liste chargée, aucun fan-out par Space.
     */
    long countClients(UUID orgId);
}
