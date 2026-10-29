package com.takibo.securitymanagement.domain.service.policy;

import com.takibo.securitymanagement.domain.model.Action;
import com.takibo.securitymanagement.domain.model.PolicyDecision;
import com.takibo.securitymanagement.domain.model.Resource;
import com.takibo.securitymanagement.domain.model.Subject;

import java.util.Optional;

import static com.takibo.securitymanagement.domain.service.policy.Decisions.deny;
import static com.takibo.securitymanagement.domain.service.policy.Decisions.permit;

/**
 * POST /api/v1/orgs/signup: the bootstrap remains authenticated as before this
 * story, but its authorization is now explicit. Every other action is denied
 * instead of falling through to POL_DEFAULT_ALLOW.
 */
public final class OrganizationSignupPolicy implements ResourcePolicy {

    private static final String SIGNUP_PATH = RequestPath.route("api", "v1", "orgs", "signup");

    @Override
    public Optional<PolicyDecision> evaluate(Subject subject, Resource resource, Action action) {
        return Optional.of(resource.path())
                .filter(SIGNUP_PATH::equals)
                .map(path -> action == Action.CREATE
                        ? permit("POL_ORG_SIGNUP_AUTHENTICATED_CREATE",
                                "An authenticated caller may bootstrap a new organization")
                        : deny("POL_ORG_SIGNUP_ACTION_NOT_SUPPORTED",
                                "Only CREATE is governed on the organization signup route"));
    }
}
