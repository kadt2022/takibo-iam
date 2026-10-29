package com.takibo.securitymanagement.domain.service.policy;

import com.takibo.securitymanagement.domain.model.Action;
import com.takibo.securitymanagement.domain.model.PolicyDecision;
import com.takibo.securitymanagement.domain.model.Resource;
import com.takibo.securitymanagement.domain.model.Subject;

import java.util.Optional;

import static com.takibo.securitymanagement.domain.service.policy.PolicyConditions.isUuid;
import static com.takibo.securitymanagement.domain.service.policy.PolicyDecisions.deny;

/**
 * Terminal lock for the TMS surface. After the explicit signup, dashboard,
 * spaces and clients policies, any other route using TMS UUID identification is
 * denied. TIS-CORE routes using readable organization codes remain out of scope.
 * Must stay last in the chain.
 */
public final class UngovernedTmsPolicy implements ResourcePolicy {

    @Override
    public Optional<PolicyDecision> evaluate(Subject subject, Resource resource, Action action) {
        String[] segments = resource.path().split("/");
        boolean tmsOrganizationRoute = segments.length >= 5
                && segments[0].isEmpty()
                && "api".equals(segments[1])
                && "v1".equals(segments[2])
                && "orgs".equals(segments[3])
                && isUuid(segments[4]);

        if (!tmsOrganizationRoute) {
            return Optional.empty();
        }
        return deny("POL_TMS_ROUTE_NOT_GOVERNED",
                "No explicit policy governs this TMS route - denied by default");
    }
}
