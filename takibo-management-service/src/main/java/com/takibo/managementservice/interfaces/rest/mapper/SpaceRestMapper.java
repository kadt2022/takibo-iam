package com.takibo.managementservice.interfaces.rest.mapper;

import com.takibo.managementservice.application.command.CreateSpaceResult;
import com.takibo.managementservice.application.query.result.SpaceDetailsResult;
import com.takibo.managementservice.application.query.result.SpacePageResult;
import com.takibo.managementservice.application.query.result.SpaceSummaryResult;
import com.takibo.managementservice.interfaces.rest.response.SpacePageResponse;
import com.takibo.managementservice.interfaces.rest.response.SpaceResponse;
import com.takibo.managementservice.interfaces.rest.response.SpaceSummaryResponse;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;

/**
 * Frontière REST du read-side spaces : résultats applicatifs -> réponses HTTP.
 * La couche application ne connaît pas ces types.
 */
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface SpaceRestMapper {

    SpaceResponse toSpaceResponse(CreateSpaceResult result);

    SpaceSummaryResponse toSummaryResponse(SpaceSummaryResult result);

    SpaceResponse toSpaceResponse(SpaceDetailsResult result);

    SpacePageResponse toPageResponse(SpacePageResult result);
}
