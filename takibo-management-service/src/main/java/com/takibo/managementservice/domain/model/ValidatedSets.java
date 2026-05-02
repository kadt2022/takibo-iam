package com.takibo.managementservice.domain.model;

import java.util.Set;

/**
 * Conteneur immuable des ensembles normalisés/validés pour l'enregistrement d'un client.
 */
public record ValidatedSets(
    Set<String> grantTypes,
    Set<String> scopes,
    Set<String> redirectUris,
    Set<String> postLogoutRedirectUris,
    Set<String> corsOrigins
) {}
