package com.takibo.managementservice.infrastructure.jpa.query;

import com.takibo.managementservice.application.query.result.SpaceDetailsResult;
import com.takibo.managementservice.application.query.result.SpacePageResult;
import com.takibo.managementservice.domain.exception.SpaceNotFoundException;
import com.takibo.managementservice.domain.model.SpaceStatus;
import com.takibo.managementservice.infrastructure.entity.SpaceEntity;
import com.takibo.managementservice.infrastructure.jpa.repository.JpaSpaceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mapstruct.factory.Mappers;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JpaSpaceQueryAdapterTest {

    private static final UUID ORG_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID SPACE_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");

    @Mock
    private JpaSpaceRepository repository;

    private JpaSpaceQueryAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new JpaSpaceQueryAdapter(repository, Mappers.getMapper(JpaSpaceQueryMapper.class));
    }

    private SpaceEntity entity() {
        return SpaceEntity.builder()
                .id(SPACE_ID)
                .orgId(ORG_ID)
                .code("busa")
                .name("Busa")
                .description("Espace principal de Busa")
                .status(SpaceStatus.SUSPENDED)
                .statusReason("Investigation de sécurité en cours")
                .statusUpdatedAt(Instant.parse("2026-07-10T14:30:00Z"))
                .ownerAccountId(UUID.fromString("33333333-3333-3333-3333-333333333333"))
                .createdAt(Instant.parse("2026-07-10T12:00:00Z"))
                .updatedAt(Instant.parse("2026-07-10T14:30:00Z"))
                .version(3L)
                .build();
    }

    // ===== Liste =====

    @Test
    void listSpaces_withoutSearch_clampsPagingAndSortsByCreatedAtDesc() {
        ArgumentCaptor<PageRequest> pageCaptor = ArgumentCaptor.forClass(PageRequest.class);
        when(repository.findPageByOrg(eq(ORG_ID), eq(null), any(PageRequest.class)))
                .thenAnswer(inv -> new PageImpl<>(List.of(entity()), inv.getArgument(2), 1));

        SpacePageResult page = adapter.listSpaces(ORG_ID, null, null, -5, 500, null);

        verify(repository).findPageByOrg(eq(ORG_ID), eq(null), pageCaptor.capture());
        assertThat(pageCaptor.getValue().getPageNumber()).isZero();
        assertThat(pageCaptor.getValue().getPageSize()).isEqualTo(100);
        assertThat(pageCaptor.getValue().getSort())
                .isEqualTo(Sort.by(Sort.Direction.DESC, "createdAt"));

        assertThat(page.totalElements()).isEqualTo(1);
        assertThat(page.totalPages()).isEqualTo(1);
        assertThat(page.content()).hasSize(1);
        assertThat(page.content().get(0).id()).isEqualTo(SPACE_ID);
        assertThat(page.content().get(0).code()).isEqualTo("busa");
        assertThat(page.content().get(0).status()).isEqualTo(SpaceStatus.SUSPENDED);
    }

    @Test
    void listSpaces_zeroSize_clampedToOne() {
        when(repository.findPageByOrg(eq(ORG_ID), eq(null), any(PageRequest.class)))
                .thenAnswer(inv -> new PageImpl<>(List.of(), inv.getArgument(2), 0));

        adapter.listSpaces(ORG_ID, null, null, 0, 0, null);

        verify(repository).findPageByOrg(eq(ORG_ID), eq(null),
                eq(PageRequest.of(0, 1, Sort.by(Sort.Direction.DESC, "createdAt"))));
    }

    @Test
    void listSpaces_withSearch_usesLowercasePatternAndStatusFilter() {
        when(repository.searchPageByOrg(eq(ORG_ID), eq(SpaceStatus.ACTIVE), eq("%busa%"), any(PageRequest.class)))
                .thenAnswer(inv -> new PageImpl<>(List.of(), inv.getArgument(3), 0));

        SpacePageResult page = adapter.listSpaces(ORG_ID, SpaceStatus.ACTIVE, "  BuSa ", 0, 20, "name,asc");

        verify(repository).searchPageByOrg(eq(ORG_ID), eq(SpaceStatus.ACTIVE), eq("%busa%"),
                eq(PageRequest.of(0, 20, Sort.by(Sort.Direction.ASC, "name"))));
        assertThat(page.content()).isEmpty();
    }

    // ===== Tri strict =====

    @Test
    void listSpaces_acceptsExplicitDescDirection() {
        when(repository.findPageByOrg(eq(ORG_ID), eq(null), any(PageRequest.class)))
                .thenAnswer(inv -> new PageImpl<>(List.of(), inv.getArgument(2), 0));

        adapter.listSpaces(ORG_ID, null, null, 0, 20, "updatedAt,desc");

        verify(repository).findPageByOrg(eq(ORG_ID), eq(null),
                eq(PageRequest.of(0, 20, Sort.by(Sort.Direction.DESC, "updatedAt"))));
    }

    @Test
    void listSpaces_rejectsNonWhitelistedSortField() {
        assertThatThrownBy(() -> adapter.listSpaces(ORG_ID, null, null, 0, 20, "ownerAccountId,asc"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("sort field");
    }

    @Test
    void listSpaces_rejectsUnknownSortDirection() {
        // Aucune direction inconnue ne doit retomber silencieusement sur ASC.
        for (String sort : new String[]{"name,banana", "name,descending", "name,"}) {
            assertThatThrownBy(() -> adapter.listSpaces(ORG_ID, null, null, 0, 20, sort))
                    .as(sort)
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("direction");
        }
    }

    @Test
    void listSpaces_rejectsMoreThanTwoSortSegments() {
        assertThatThrownBy(() -> adapter.listSpaces(ORG_ID, null, null, 0, 20, "name,asc,garbage"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("expression");
    }

    @Test
    void listSpaces_rejectsEmptySortField() {
        assertThatThrownBy(() -> adapter.listSpaces(ORG_ID, null, null, 0, 20, ",asc"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("sort field");
    }

    // ===== Détail =====

    @Test
    void getSpace_mapsStatusReasonStatusUpdatedAtAndVersion() {
        when(repository.findByIdAndOrgId(SPACE_ID, ORG_ID)).thenReturn(Optional.of(entity()));

        SpaceDetailsResult result = adapter.getSpace(ORG_ID, SPACE_ID);

        assertThat(result.id()).isEqualTo(SPACE_ID);
        assertThat(result.description()).isEqualTo("Espace principal de Busa");
        assertThat(result.statusReason()).isEqualTo("Investigation de sécurité en cours");
        assertThat(result.statusUpdatedAt()).isEqualTo(Instant.parse("2026-07-10T14:30:00Z"));
        assertThat(result.version()).isEqualTo(3L);
    }

    @Test
    void getSpace_unknownOrForeignSpace_throwsTmsNotFound() {
        // Même 404 pour « inexistant » et « existe dans une autre org » : anti-énumération,
        // et le message ne révèle rien.
        when(repository.findByIdAndOrgId(SPACE_ID, ORG_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> adapter.getSpace(ORG_ID, SPACE_ID))
                .isInstanceOf(SpaceNotFoundException.class)
                .hasMessage("Space not found in organization");
    }
}
