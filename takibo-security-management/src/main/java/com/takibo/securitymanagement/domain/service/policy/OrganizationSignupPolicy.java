package com.takibo.securitymanagement.domain.service.policy;

import com.takibo.securitymanagement.domain.model.Action;
import com.takibo.securitymanagement.domain.model.PolicyDecision;
import com.takibo.securitymanagement.domain.model.Resource;
import com.takibo.securitymanagement.domain.model.Subject;

import java.util.Optional;

import static com.takibo.securitymanagement.domain.service.policy.PolicyDecisions.deny;
import static com.takibo.securitymanagement.domain.service.policy.PolicyDecisions.permit;

/**
 * POST /api/v1/orgs/signup: the bootstrap remains authenticated as before this
 * story, but its authorization is now explicit. Every other action is denied
 * instead of falling through to POL_DEFAULT_ALLOW.
 */
public final class OrganizationSignupPolicy implements ResourcePolicy {

    @Override
    public Optional<PolicyDecision> evaluate(Subject subject, Resource resource, Action action) {
        if (!"/api/v1/orgs/signup".equals(resource.path())) {
            return Optional.empty();
        }
        if (action != Action.CREATE) {
            return deny("POL_ORG_SIGNUP_ACTION_NOT_SUPPORTED",
                    "Only CREATE is governed on the organization signup route");
        }
        return permit("POL_ORG_SIGNUP_AUTHENTICATED_CREATE",
                "An authenticated caller may bootstrap a new organization");
    }
}
