package com.takibo.managementservice.application.port;

import java.util.UUID;

/**
 * Frontière TMS -> TIS-CORE pour le provisioning RBAC technique. La couche
 * application exprime l'intention (fondateur, créateur de space) ; seul un
 * adaptateur d'intégration connaît les cas d'usage RBAC et le domaine identité.
 */
public interface TechnicalRbacProvisioningPort {

    void provisionFounder(UUID orgId,
                          UUID initialSpaceId,
                          UUID founderAccountId,
                          String systemActor);

    void provisionSpaceCreator(UUID orgId,
                               UUID spaceId,
                               UUID creatorAccountId,
                               String systemActor);
}
