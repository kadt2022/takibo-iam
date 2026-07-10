package com.takibo.managementservice.application.query.port;

import com.takibo.managementservice.application.query.result.SpaceDetailsResult;
import com.takibo.managementservice.application.query.result.SpacePageResult;
import com.takibo.managementservice.domain.model.SpaceStatus;

import java.util.UUID;

/**
 * Port de lecture des spaces d'une organisation. La couche application définit le
 * contrat ; l'implémentation vit dans l'infrastructure (JPA). La frontière
 * (token.org_id == path.orgId, autorité ORG) est garantie en amont par le
 * PolicyEvaluator ; ici la recherche reste située par orgId.
 */
public interface SpaceQueryCase {

    SpacePageResult listSpaces(UUID orgId,
                               SpaceStatus status,
                               String search,
                               int page,
                               int size,
                               String sort);

    /**
     * @throws com.takibo.managementservice.domain.exception.SpaceNotFoundException
     *         si le space n'existe pas dans cette organisation — un space d'une
     *         autre org N'EXISTE PAS (404, jamais de 403 qui confirmerait son
     *         existence ailleurs).
     */
    SpaceDetailsResult getSpace(UUID orgId, UUID spaceId);
}
