package com.takibo.identitycore.application.spacecontext.service;

import com.takibo.identitycore.application.spacecontext.model.UserSpaceMembership;
import com.takibo.identitycore.application.spacecontext.port.UserSpaceMembershipQueryRepository;
import com.takibo.identitycore.domain.status.UserStatus;
import com.takibo.identitycore.integration.security.port.CurrentAccountContextCase;
import com.takibo.identitycore.integration.security.port.CurrentOrganizationContextCase;
import com.takibo.identitycore.integration.space.port.SpaceContextCatalogCase;
import com.takibo.identitycore.integration.space.port.SpaceContextSummary;
import com.takibo.identitycore.interfaces.rest.response.CurrentUserSpacesResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CurrentUserSpaceQueryServiceTest {

    private static final UUID ORG_ID = UUID.fromString("aaaaaaaa-0000-0000-0000-000000000001");
    private static final UUID OTHER_ORG_ID = UUID.fromString("99999999-0000-0000-0000-000000000009");
    private static final UUID ACCOUNT_ID = UUID.fromString("bbbbbbbb-0000-0000-0000-000000000002");
    private static final UUID FINANCE_SPACE_ID = UUID.fromString("cccccccc-0000-0000-0000-000000000003");
    private static final UUID ARCHIVES_SPACE_ID = UUID.fromString("dddddddd-0000-0000-0000-000000000004");
    private static final UUID FOREIGN_SPACE_ID = UUID.fromString("eeeeeeee-0000-0000-0000-000000000005");
    private static final UUID FINANCE_USER_ID = UUID.fromString("11111111-0000-0000-0000-000000000001");
    private static final UUID ARCHIVES_USER_ID = UUID.fromString("22222222-0000-0000-0000-000000000002");
    private static final UUID FOREIGN_USER_ID = UUID.fromString("33333333-0000-0000-0000-000000000003");

    @Mock private CurrentOrganizationContextCase currentOrganizationContext;
    @Mock private CurrentAccountContextCase currentAccountContext;
    @Mock private UserSpaceMembershipQueryRepository memberships;
    @Mock private SpaceContextCatalogCase spaceCatalog;

    @InjectMocks private CurrentUserSpaceQueryService service;

    @Test
    void listAccessibleSpaces_returnsOnlyCurrentAccountMembershipsWithSelectableFlagAndStableSort() {
        stubContext();
        when(memberships.findByOrganizationAndAccount(ORG_ID, ACCOUNT_ID)).thenReturn(List.of(
                new UserSpaceMembership(ARCHIVES_SPACE_ID, ARCHIVES_USER_ID, UserStatus.ACTIVE),
                new UserSpaceMembership(FINANCE_SPACE_ID, FINANCE_USER_ID, UserStatus.ACTIVE)));
        when(spaceCatalog.findByOrganizationAndIds(ORG_ID, Set.of(ARCHIVES_SPACE_ID, FINANCE_SPACE_ID)))
                .thenReturn(List.of(
                        new SpaceContextSummary(ORG_ID, ARCHIVES_SPACE_ID, "archives", "Archives", "SUSPENDED"),
                        new SpaceContextSummary(ORG_ID, FINANCE_SPACE_ID, "finance", "Finance", "ACTIVE")));

        CurrentUserSpacesResponse response = service.listAccessibleSpaces();

        assertThat(response.organizationId()).isEqualTo(ORG_ID);
        assertThat(response.items()).extracting("code").containsExactly("archives", "finance");
        assertThat(response.items()).extracting("userId").containsExactly(ARCHIVES_USER_ID, FINANCE_USER_ID);
        assertThat(response.items()).extracting("selectable").containsExactly(false, true);
    }

    @Test
    void listAccessibleSpaces_emptyMemberships_returnsEmptyResponseWithoutTmsResolution() {
        stubContext();
        when(memberships.findByOrganizationAndAccount(ORG_ID, ACCOUNT_ID)).thenReturn(List.of());

        CurrentUserSpacesResponse response = service.listAccessibleSpaces();

        assertThat(response.organizationId()).isEqualTo(ORG_ID);
        assertThat(response.items()).isEmpty();
        verify(spaceCatalog, never()).findByOrganizationAndIds(org.mockito.Mockito.any(), org.mockito.Mockito.any());
    }

    @Test
    void listAccessibleSpaces_resolvesSpacesInOneBatch() {
        stubContext();
        when(memberships.findByOrganizationAndAccount(ORG_ID, ACCOUNT_ID)).thenReturn(List.of(
                new UserSpaceMembership(FINANCE_SPACE_ID, FINANCE_USER_ID, UserStatus.ACTIVE),
                new UserSpaceMembership(ARCHIVES_SPACE_ID, ARCHIVES_USER_ID, UserStatus.SUSPENDED)));
        when(spaceCatalog.findByOrganizationAndIds(org.mockito.Mockito.eq(ORG_ID), org.mockito.Mockito.anySet()))
                .thenReturn(List.of(
                        new SpaceContextSummary(ORG_ID, FINANCE_SPACE_ID, "finance", "Finance", "ACTIVE"),
                        new SpaceContextSummary(ORG_ID, ARCHIVES_SPACE_ID, "archives", "Archives", "ACTIVE")));

        service.listAccessibleSpaces();

        ArgumentCaptor<Set<UUID>> ids = ArgumentCaptor.forClass(Set.class);
        verify(spaceCatalog).findByOrganizationAndIds(org.mockito.Mockito.eq(ORG_ID), ids.capture());
        assertThat(ids.getValue()).containsExactlyInAnyOrder(FINANCE_SPACE_ID, ARCHIVES_SPACE_ID);
    }

    @Test
    void listAccessibleSpaces_filtersTmsResponsesOutsideCurrentOrganization() {
        stubContext();
        when(memberships.findByOrganizationAndAccount(ORG_ID, ACCOUNT_ID)).thenReturn(List.of(
                new UserSpaceMembership(FINANCE_SPACE_ID, FINANCE_USER_ID, UserStatus.ACTIVE),
                new UserSpaceMembership(FOREIGN_SPACE_ID, FOREIGN_USER_ID, UserStatus.ACTIVE)));
        when(spaceCatalog.findByOrganizationAndIds(org.mockito.Mockito.eq(ORG_ID), org.mockito.Mockito.anySet()))
                .thenReturn(List.of(
                        new SpaceContextSummary(ORG_ID, FINANCE_SPACE_ID, "finance", "Finance", "ACTIVE"),
                        new SpaceContextSummary(OTHER_ORG_ID, FOREIGN_SPACE_ID, "foreign", "Foreign", "ACTIVE")));

        CurrentUserSpacesResponse response = service.listAccessibleSpaces();

        assertThat(response.items()).extracting("spaceId").containsExactly(FINANCE_SPACE_ID);
    }

    @Test
    void listAccessibleSpaces_userSuspended_isVisibleButNotSelectable() {
        stubContext();
        when(memberships.findByOrganizationAndAccount(ORG_ID, ACCOUNT_ID)).thenReturn(List.of(
                new UserSpaceMembership(FINANCE_SPACE_ID, FINANCE_USER_ID, UserStatus.SUSPENDED)));
        when(spaceCatalog.findByOrganizationAndIds(org.mockito.Mockito.eq(ORG_ID), org.mockito.Mockito.anySet()))
                .thenReturn(List.of(
                        new SpaceContextSummary(ORG_ID, FINANCE_SPACE_ID, "finance", "Finance", "ACTIVE")));

        CurrentUserSpacesResponse response = service.listAccessibleSpaces();

        assertThat(response.items()).singleElement().satisfies(item -> {
            assertThat(item.userStatus()).isEqualTo(UserStatus.SUSPENDED);
            assertThat(item.selectable()).isFalse();
        });
    }

    private void stubContext() {
        when(currentOrganizationContext.requireCurrentOrganizationId()).thenReturn(ORG_ID);
        when(currentAccountContext.requireCurrentAccountId()).thenReturn(ACCOUNT_ID);
    }
}
