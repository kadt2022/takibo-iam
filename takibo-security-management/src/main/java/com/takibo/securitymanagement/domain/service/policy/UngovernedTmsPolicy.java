package com.takibo.securitymanagement.domain.service.policy;

import com.takibo.securitymanagement.domain.model.Action;
import com.takibo.securitymanagement.domain.model.PolicyDecision;
import com.takibo.securitymanagement.domain.model.Resource;
import com.takibo.securitymanagement.domain.model.Subject;

import java.util.Optional;

import static com.takibo.securitymanagement.domain.service.policy.Decisions.deny;

/**
 * Terminal lock for the TMS surface. After the explicit signup, dashboard,
 * spaces and clients policies, any other route using TMS UUID identification is
 * denied. TIS-CORE routes using readable organization codes remain out of scope.
 * Must stay last in the chain.
 */
public final class UngovernedTmsPolicy implements ResourcePolicy {

    @Override
    public Optional<PolicyDecision> evaluate(Subject subject, Resource resource, Action action) {
        return Optional.of(RequestPath.of(resource.path()))
                .filter(request -> request.isOrganizationRoute(5) && request.hasUuidOrganization())
                .map(request -> deny("POL_TMS_ROUTE_NOT_GOVERNED",
                        "No explicit policy governs this TMS route - denied by default"));
    }
}
