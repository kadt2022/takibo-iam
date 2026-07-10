package com.takibo.managementservice.interfaces.rest.api;

import com.takibo.managementservice.application.command.CreateSpaceCommand;
import com.takibo.managementservice.application.port.CurrentActorProvider;
import com.takibo.managementservice.application.security.ActorSource;
import com.takibo.managementservice.application.service.SpaceApplicationService;
import com.takibo.managementservice.application.service.SpaceQueryService;
import com.takibo.managementservice.domain.model.SpaceStatus;
import com.takibo.managementservice.interfaces.rest.response.SpacePageResponse;
import com.takibo.managementservice.interfaces.rest.response.SpaceResponse;
import com.takibo.managementservice.interfaces.rest.response.SpaceSummaryResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class SpaceControllerTest {

    private static final UUID ORG_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID SPACE_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID OWNER_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");
    private static final String BASE = "/api/v1/orgs/" + ORG_ID + "/spaces";

    @Mock
    private SpaceApplicationService service;

    @Mock
    private SpaceQueryService queryService;

    @Mock
    private CurrentActorProvider actorProvider;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        SpaceController controller = new SpaceController(service, queryService, actorProvider);
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    private SpaceResponse spaceResponse() {
        return new SpaceResponse(
                SPACE_ID, ORG_ID, "busa", "Busa", "Espace principal de Busa",
                SpaceStatus.ACTIVE, null, Instant.parse("2026-07-10T12:00:00Z"),
                OWNER_ID,
                Instant.parse("2026-07-10T12:00:00Z"), Instant.parse("2026-07-10T12:00:00Z"), 0L);
    }

    @Test
    void createSpace_returns201WithLocationHeader() throws Exception {
        when(actorProvider.currentUserId()).thenReturn(OWNER_ID);
        when(actorProvider.source()).thenReturn(ActorSource.HUMAN);
        when(service.createSpace(any(CreateSpaceCommand.class))).thenReturn(spaceResponse());

        mockMvc.perform(post(BASE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Busa\",\"code\":\"busa\"}"))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "http://localhost" + BASE + "/" + SPACE_ID))
                .andExpect(jsonPath("$.id").value(SPACE_ID.toString()))
                .andExpect(jsonPath("$.code").value("busa"));
    }

    @Test
    void listSpaces_delegatesFiltersAndPaginationToQueryService() throws Exception {
        SpaceSummaryResponse summary = new SpaceSummaryResponse(
                SPACE_ID, ORG_ID, "busa", "Busa", SpaceStatus.ACTIVE, OWNER_ID,
                Instant.parse("2026-07-10T12:00:00Z"), Instant.parse("2026-07-10T12:00:00Z"));
        when(queryService.listSpaces(ORG_ID, SpaceStatus.ACTIVE, "bu", 1, 5, "name,asc"))
                .thenReturn(new SpacePageResponse(List.of(summary), 1, 5, 6, 2));

        mockMvc.perform(get(BASE)
                        .param("status", "ACTIVE")
                        .param("search", "bu")
                        .param("page", "1")
                        .param("size", "5")
                        .param("sort", "name,asc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(SPACE_ID.toString()))
                .andExpect(jsonPath("$.content[0].code").value("busa"))
                .andExpect(jsonPath("$.page").value(1))
                .andExpect(jsonPath("$.size").value(5))
                .andExpect(jsonPath("$.totalElements").value(6))
                .andExpect(jsonPath("$.totalPages").value(2));

        verify(queryService).listSpaces(ORG_ID, SpaceStatus.ACTIVE, "bu", 1, 5, "name,asc");
    }

    @Test
    void listSpaces_defaultsWithoutParams() throws Exception {
        when(queryService.listSpaces(ORG_ID, null, null, 0, 20, null))
                .thenReturn(new SpacePageResponse(List.of(), 0, 20, 0, 0));

        mockMvc.perform(get(BASE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isEmpty());

        verify(queryService).listSpaces(ORG_ID, null, null, 0, 20, null);
    }

    @Test
    void getSpace_returnsDetail() throws Exception {
        when(queryService.getSpace(ORG_ID, SPACE_ID)).thenReturn(spaceResponse());

        mockMvc.perform(get(BASE + "/" + SPACE_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(SPACE_ID.toString()))
                .andExpect(jsonPath("$.orgId").value(ORG_ID.toString()))
                .andExpect(jsonPath("$.version").value(0));
    }
}
