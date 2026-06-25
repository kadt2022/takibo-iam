package com.takibo.identitycore.application.identity.port;

import com.takibo.identitycore.application.identity.command.ProvisionFounderUserCommand;
import com.takibo.identitycore.interfaces.rest.response.UserResponse;

/**
 * Cas d'usage INTERNE de provisioning du fondateur pendant le bootstrap d'une organisation.
 * <p>
 * Doctrine : un accès normal vérifie une frontière existante ; un signup fondateur
 * <em>crée</em> la frontière. Ce port ne dépend donc PAS du contexte d'organisation
 * courant (token) — contrairement à {@link com.takibo.identitycore.application.identity.port.UserApplicationCase}.
 * <p>
 * <strong>Ne JAMAIS exposer via un contrôleur REST.</strong> Il contourne volontairement
 * la garde d'ownership d'org ; il doit rester appelé en in-process, uniquement par le
 * flux de signup d'organisation.
 */
public interface FounderUserProvisioningCase {

    UserResponse provisionFounder(ProvisionFounderUserCommand command);
}
