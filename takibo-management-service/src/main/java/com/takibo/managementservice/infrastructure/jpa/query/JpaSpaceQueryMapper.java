package com.takibo.managementservice.infrastructure.jpa.query;

import com.takibo.managementservice.application.query.result.SpaceDetailsResult;
import com.takibo.managementservice.application.query.result.SpaceSummaryResult;
import com.takibo.managementservice.infrastructure.entity.SpaceEntity;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;

/**
 * Projection read-side : SpaceEntity -> résultats applicatifs, sans reconstruire
 * l'agrégat Space — la lecture ne porte aucune règle métier (le write-side, lui,
 * continue de passer par le domaine).
 */
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface JpaSpaceQueryMapper {

    SpaceSummaryResult toSummaryResult(SpaceEntity entity);

    SpaceDetailsResult toDetailsResult(SpaceEntity entity);
}
