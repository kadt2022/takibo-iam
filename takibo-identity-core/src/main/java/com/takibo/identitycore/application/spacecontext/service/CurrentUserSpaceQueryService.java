package com.takibo.identitycore.application.spacecontext.service;

import com.takibo.identitycore.application.spacecontext.model.UserSpaceMembership;
import com.takibo.identitycore.application.spacecontext.port.UserSpaceMembershipQueryRepository;
import com.takibo.identitycore.application.spacecontext.port.in.CurrentUserSpaceQueryCase;
import com.takibo.identitycore.domain.status.UserStatus;
import com.takibo.identitycore.integration.security.port.CurrentAccountContextCase;
import com.takibo.identitycore.integration.security.port.CurrentOrganizationContextCase;
import com.takibo.identitycore.integration.space.port.SpaceContextCatalogCase;
import com.takibo.identitycore.integration.space.port.SpaceContextSummary;
import com.takibo.identitycore.interfaces.rest.response.CurrentUserSpaceItemResponse;
import com.takibo.identitycore.interfaces.rest.response.CurrentUserSpacesResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class CurrentUserSpaceQueryService implements CurrentUserSpaceQueryCase {

    private static final String ACTIVE = "ACTIVE";

    private final CurrentOrganizationContextCase currentOrganizationContext;
    private final CurrentAccountContextCase currentAccountContext;
    private final UserSpaceMembershipQueryRepository memberships;
    private final SpaceContextCatalogCase spaceCatalog;

    @Override
    @Transactional(readOnly = true)
    public CurrentUserSpacesResponse listAccessibleSpaces() {
        UUID organizationId = currentOrganizationContext.requireCurrentOrganizationId();
        UUID accountId = currentAccountContext.requireCurrentAccountId();

        List<UserSpaceMembership> localMemberships =
                memberships.findByOrganizationAndAccount(organizationId, accountId);
        if (localMemberships.isEmpty()) {
            return new CurrentUserSpacesResponse(organizationId, List.of());
        }

        Set<UUID> spaceIds = localMemberships.stream()
                .map(UserSpaceMembership::spaceId)
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));

        Map<UUID, SpaceContextSummary> summariesById = spaceCatalog
                .findByOrganizationAndIds(organizationId, spaceIds)
                .stream()
                .filter(summary -> isInsideBoundary(organizationId, summary))
                .collect(Collectors.toMap(
                        SpaceContextSummary::id,
                        Function.identity(),
                        (left, right) -> left));

        List<CurrentUserSpaceItemResponse> items = localMemberships.stream()
                .map(membership -> toResponseItem(membership, summariesById.get(membership.spaceId())))
                .filter(Objects::nonNull)
                .sorted(Comparator
                        .comparing(CurrentUserSpaceItemResponse::name, CurrentUserSpaceQueryService::compareNullable)
                        .thenComparing(CurrentUserSpaceItemResponse::code, CurrentUserSpaceQueryService::compareNullable))
                .toList();

        return new CurrentUserSpacesResponse(organizationId, items);
    }

    private boolean isInsideBoundary(UUID organizationId, SpaceContextSummary summary) {
        if (summary == null || summary.id() == null) {
            return false;
        }
        if (organizationId.equals(summary.organizationId())) {
            return true;
        }
        log.warn("Boundary anomaly: TMS returned space outside current organization orgId={} spaceId={} actualOrgId={}",
                organizationId, summary.id(), summary.organizationId());
        return false;
    }

    private CurrentUserSpaceItemResponse toResponseItem(UserSpaceMembership membership,
                                                       SpaceContextSummary summary) {
        if (summary == null) {
            log.warn("Boundary anomaly: local user membership references an unresolved space spaceId={} userId={}",
                    membership.spaceId(), membership.userId());
            return null;
        }

        boolean selectable = ACTIVE.equals(summary.status()) && membership.userStatus() == UserStatus.ACTIVE;
        return new CurrentUserSpaceItemResponse(
                summary.id(),
                summary.code(),
                summary.name(),
                membership.userId(),
                summary.status(),
                membership.userStatus(),
                selectable
        );
    }

    private static int compareNullable(String left, String right) {
        if (left == null && right == null) return 0;
        if (left == null) return 1;
        if (right == null) return -1;
        return left.toLowerCase(Locale.ROOT).compareTo(right.toLowerCase(Locale.ROOT));
    }
}
