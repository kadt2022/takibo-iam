package com.takibo.managementservice.application.port;

import java.net.URI;

/**
 * Décide si une {@code jwksUri} peut être enregistrée : transport (HTTPS, ou HTTP vers une
 * destination de confiance en développement) et destination (jamais interne sans exception
 * déclarée). C'est la même politique que celle qui s'applique quand Takibo récupère les clés ;
 * à l'enregistrement elle juge l'URI sans DNS, la récupération restant le contrôle qui fait foi.
 */
public interface JwksUriAdmissionPort {

    /**
     * @throws com.takibo.managementservice.domain.exception.InvalidClientConfigurationException
     *         si la destination est refusée
     */
    void requireAllowed(URI jwksUri);
}
