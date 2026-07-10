package com.takibo.managementservice.interfaces.rest.mapper;

import com.takibo.managementservice.application.query.result.SpaceDetailsResult;
import com.takibo.managementservice.application.query.result.SpacePageResult;
import com.takibo.managementservice.application.query.result.SpaceSummaryResult;
import com.takibo.managementservice.domain.model.SpaceStatus;
import com.takibo.managementservice.interfaces.rest.response.SpacePageResponse;
import com.takibo.managementservice.interfaces.rest.response.SpaceResponse;
import com.takibo.managementservice.interfaces.rest.response.SpaceSummaryResponse;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class SpaceRestMapperTest {

    private static final UUID ORG_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID SPACE_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID OWNER_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");
    private static final Instant CREATED = Instant.parse("2026-07-10T12:00:00Z");
    private static final Instant UPDATED = Instant.parse("2026-07-10T14:30:00Z");

    private final SpaceRestMapper mapper = Mappers.getMapper(SpaceRestMapper.class);

    private SpaceSummaryResult summaryResult() {
        return new SpaceSummaryResult(SPACE_ID, ORG_ID, "busa", "Busa",
                SpaceStatus.ACTIVE, OWNER_ID, CREATED, UPDATED);
    }

    @Test
    void summaryResult_mapsToSummaryResponse() {
        SpaceSummaryResponse response = mapper.toSummaryResponse(summaryResult());

        assertThat(response).isEqualTo(new SpaceSummaryResponse(
                SPACE_ID, ORG_ID, "busa", "Busa", SpaceStatus.ACTIVE, OWNER_ID, CREATED, UPDATED));
    }

    @Test
    void detailsResult_mapsToSpaceResponse() {
        SpaceDetailsResult details = new SpaceDetailsResult(
                SPACE_ID, ORG_ID, "busa", "Busa", "Espace principal de Busa",
                SpaceStatus.SUSPENDED, "Investigation de sécurité en cours", UPDATED,
                OWNER_ID, CREATED, UPDATED, 3L);

        SpaceResponse response = mapper.toSpaceResponse(details);

        assertThat(response).isEqualTo(new SpaceResponse(
                SPACE_ID, ORG_ID, "busa", "Busa", "Espace principal de Busa",
                SpaceStatus.SUSPENDED, "Investigation de sécurité en cours", UPDATED,
                OWNER_ID, CREATED, UPDATED, 3L));
    }

    @Test
    void pageResult_mapsToPageResponseWithContent() {
        SpacePageResult page = new SpacePageResult(List.of(summaryResult()), 1, 5, 6, 2);

        SpacePageResponse response = mapper.toPageResponse(page);

        assertThat(response.page()).isEqualTo(1);
        assertThat(response.size()).isEqualTo(5);
        assertThat(response.totalElements()).isEqualTo(6);
        assertThat(response.totalPages()).isEqualTo(2);
        assertThat(response.content()).containsExactly(new SpaceSummaryResponse(
                SPACE_ID, ORG_ID, "busa", "Busa", SpaceStatus.ACTIVE, OWNER_ID, CREATED, UPDATED));
    }
}
