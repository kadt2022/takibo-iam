package com.takibo.managementservice.interfaces.rest.api;

import com.takibo.managementservice.application.command.CreateSpaceCommand;
import com.takibo.managementservice.application.port.CurrentActorProvider;
import com.takibo.managementservice.application.query.port.SpaceQueryCase;
import com.takibo.managementservice.application.query.result.SpaceDetailsResult;
import com.takibo.managementservice.application.query.result.SpacePageResult;
import com.takibo.managementservice.application.query.result.SpaceSummaryResult;
import com.takibo.managementservice.application.security.ActorSource;
import com.takibo.managementservice.application.service.SpaceApplicationService;
import com.takibo.managementservice.domain.model.SpaceStatus;
import com.takibo.managementservice.interfaces.rest.mapper.SpaceRestMapper;
import com.takibo.managementservice.interfaces.rest.response.SpaceResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mapstruct.factory.Mappers;
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

/**
 * Test du contrôleur seul (standalone MockMvc) : délégation vers SpaceQueryCase et
 * mapping applicatif -> REST. Les gardes de sécurité sont prouvées séparément par
 * le test d'intégration qui traverse la vraie chaîne Spring Security.
 */
@ExtendWith(MockitoExtension.class)
class SpaceControllerTest {

    private static final UUID ORG_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID SPACE_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID OWNER_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");
    private static final String BASE = "/api/v1/orgs/" + ORG_ID + "/spaces";
    private static final Instant CREATED_AT = Instant.parse("2026-07-10T12:00:00Z");

    @Mock
    private SpaceApplicationService service;

    @Mock
    private SpaceQueryCase spaceQueryCase;

    @Mock
    private CurrentActorProvider actorProvider;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        SpaceController controller = new SpaceController(
                service, spaceQueryCase, Mappers.getMapper(SpaceRestMapper.class), actorProvider);
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test
    void createSpace_returns201WithLocationHeader() throws Exception {
        // IAM 31 : le propriétaire d'un space est un ACCOUNT, pas un user local.
        when(actorProvider.currentAccountId()).thenReturn(OWNER_ID);
        when(actorProvider.source()).thenReturn(ActorSource.HUMAN);
        when(service.createSpace(any(CreateSpaceCommand.class))).thenReturn(new SpaceResponse(
                SPACE_ID, ORG_ID, "busa", "Busa", null,
                SpaceStatus.ACTIVE, null, CREATED_AT, OWNER_ID, CREATED_AT, CREATED_AT, 0L));

        mockMvc.perform(post(BASE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Busa\",\"code\":\"busa\"}"))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "http://localhost" + BASE + "/" + SPACE_ID))
                .andExpect(jsonPath("$.id").value(SPACE_ID.toString()))
                .andExpect(jsonPath("$.code").value("busa"));
    }

    @Test
    void listSpaces_delegatesFiltersAndMapsResultToResponse() throws Exception {
        SpaceSummaryResult summary = new SpaceSummaryResult(
                SPACE_ID, ORG_ID, "busa", "Busa", SpaceStatus.ACTIVE, OWNER_ID, CREATED_AT, CREATED_AT);
        when(spaceQueryCase.listSpaces(ORG_ID, SpaceStatus.ACTIVE, "bu", 1, 5, "name,asc"))
                .thenReturn(new SpacePageResult(List.of(summary), 1, 5, 6, 2));

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

        verify(spaceQueryCase).listSpaces(ORG_ID, SpaceStatus.ACTIVE, "bu", 1, 5, "name,asc");
    }

    @Test
    void listSpaces_defaultsWithoutParams() throws Exception {
        when(spaceQueryCase.listSpaces(ORG_ID, null, null, 0, 20, null))
                .thenReturn(new SpacePageResult(List.of(), 0, 20, 0, 0));

        mockMvc.perform(get(BASE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isEmpty());

        verify(spaceQueryCase).listSpaces(ORG_ID, null, null, 0, 20, null);
    }

    @Test
    void getSpace_delegatesAndMapsDetail() throws Exception {
        when(spaceQueryCase.getSpace(ORG_ID, SPACE_ID)).thenReturn(new SpaceDetailsResult(
                SPACE_ID, ORG_ID, "busa", "Busa", "Espace principal de Busa",
                SpaceStatus.SUSPENDED, "Investigation de sécurité en cours",
                Instant.parse("2026-07-10T14:30:00Z"), OWNER_ID,
                CREATED_AT, Instant.parse("2026-07-10T14:30:00Z"), 3L));

        mockMvc.perform(get(BASE + "/" + SPACE_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(SPACE_ID.toString()))
                .andExpect(jsonPath("$.orgId").value(ORG_ID.toString()))
                .andExpect(jsonPath("$.statusReason").value("Investigation de sécurité en cours"))
                .andExpect(jsonPath("$.version").value(3));
    }
}
