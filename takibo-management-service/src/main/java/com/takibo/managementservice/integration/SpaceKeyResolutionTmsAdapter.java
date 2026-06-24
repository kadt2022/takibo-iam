package com.takibo.managementservice.integration;

import com.takibo.identitycore.domain.exception.OrganizationNotFoundException;
import com.takibo.identitycore.domain.exception.SpaceNotFoundException;
import com.takibo.identitycore.integration.space.port.ResolvedSpaceKey;
import com.takibo.identitycore.integration.space.port.SpaceKeyResolutionCase;
import com.takibo.managementservice.application.common.TakiboCodeNormalizer;
import com.takibo.managementservice.infrastructure.entity.OrganizationEntity;
import com.takibo.managementservice.infrastructure.entity.SpaceEntity;
import com.takibo.managementservice.infrastructure.jpa.repository.JpaOrganizationRepository;
import com.takibo.managementservice.infrastructure.jpa.repository.JpaSpaceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Implémentation TMS du port {@link SpaceKeyResolutionCase} de TIS-CORE.
 * <p>
 * TMS possède le catalogue des organisations/spaces et les règles de normalisation :
 * il normalise les codes reçus, résout l'organisation par code, puis le Space par
 * {@code orgId + code}, et ne retourne que des identifiants techniques neutres.
 * Les entités JPA ne franchissent jamais cette frontière.
 */
@Component
@RequiredArgsConstructor
public class SpaceKeyResolutionTmsAdapter implements SpaceKeyResolutionCase {

    private final JpaOrganizationRepository organizations;
    private final JpaSpaceRepository spaces;

    @Override
    public ResolvedSpaceKey resolve(String orgCode, String spaceCode) {
        String normalizedOrgCode = TakiboCodeNormalizer.normalize(orgCode);
        String normalizedSpaceCode = TakiboCodeNormalizer.normalize(spaceCode);

        OrganizationEntity org = organizations.findByCode(normalizedOrgCode)
                .orElseThrow(() -> new OrganizationNotFoundException(
                        "Organization not found: " + normalizedOrgCode));

        SpaceEntity space = spaces.findByOrgIdAndCode(org.getId(), normalizedSpaceCode)
                .orElseThrow(() -> new SpaceNotFoundException(
                        "Space not found: " + normalizedSpaceCode + " in organization " + normalizedOrgCode));

        return new ResolvedSpaceKey(org.getId(), space.getId(), normalizedOrgCode, normalizedSpaceCode);
    }
}
