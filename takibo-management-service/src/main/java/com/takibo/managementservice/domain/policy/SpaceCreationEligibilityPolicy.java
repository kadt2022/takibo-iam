package com.takibo.managementservice.domain.policy;

import com.takibo.managementservice.domain.exception.OrganizationDisabledException;
import com.takibo.managementservice.domain.exception.SpaceQuotaExceededException;
import com.takibo.managementservice.domain.model.OrganizationContext;

import java.util.List;
import java.util.Optional;
import java.util.function.Function;

public final class SpaceCreationEligibilityPolicy {

    private static final int MAXIMUM_SPACES = 10;

    private final List<Function<OrganizationContext, Optional<RuntimeException>>>
            rules = List.of(
            organization -> Optional.of(organization)
                    .filter(context -> !context.enabled())
                    .map(context ->
                            new OrganizationDisabledException(context.orgId())
            ),
            organization -> Optional.of(organization)
                    .filter(context ->
                            context.hasReachedSpaceLimit(MAXIMUM_SPACES)
                    )
                    .map(context -> new SpaceQuotaExceededException(
                            context.orgId(),
                            MAXIMUM_SPACES,
                            context.currentSpaces()
                    ))
    );

    public void validateEligibility(OrganizationContext organization) {
        rules.stream()
                .map(rule -> rule.apply(organization))
                .flatMap(Optional::stream)
                .findFirst()
                .ifPresent(exception -> {
                    throw exception;
                });
    }
}
