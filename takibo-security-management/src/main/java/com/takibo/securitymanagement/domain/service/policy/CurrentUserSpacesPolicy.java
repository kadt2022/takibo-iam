package com.takibo.securitymanagement.domain.service.policy;

import com.takibo.securitymanagement.domain.model.Action;
import com.takibo.securitymanagement.domain.model.PolicyDecision;
import com.takibo.securitymanagement.domain.model.Resource;
import com.takibo.securitymanagement.domain.model.Subject;

import java.util.Optional;

import static com.takibo.securitymanagement.domain.service.policy.Decisions.Requirement.require;
import static com.takibo.securitymanagement.domain.service.policy.Decisions.firstUnmet;
import static com.takibo.securitymanagement.domain.service.policy.Decisions.permit;
import static com.takibo.securitymanagement.domain.service.policy.Subjects.isOrganizationHuman;
import static com.takibo.securitymanagement.domain.service.policy.Subjects.isPresent;

/**
 * /api/v1/me/spaces (IAM 32) : surface personnelle, sans rôle, mais strictement
 * réservée à un humain authentifié au niveau ORGANIZATION avec org_id/account_id.
 */
public final class CurrentUserSpacesPolicy implements ResourcePolicy {

    private static final String MY_SPACES_PATH = RequestPath.route("api", "v1", "me", "spaces");
    private static final String POLICY_ID = "POL_MY_SPACES_ORG_HUMAN_REQUIRED";

    @Override
    public Optional<PolicyDecision> evaluate(Subject subject, Resource resource, Action action) {
        return Optional.of(resource.path())
                .filter(MY_SPACES_PATH::equals)
                .map(path -> firstUnmet(
                        require(() -> action == Action.READ && isOrganizationAccount(subject), POLICY_ID,
                                "Organization-scoped human account token required to list accessible spaces"))
                        .orElseGet(() -> permit(POLICY_ID,
                                "Organization-scoped human account token may list its own accessible spaces")));
    }

    private static boolean isOrganizationAccount(Subject subject) {
        return isOrganizationHuman(subject)
                && isPresent(subject.orgId())
                && isPresent(subject.accountId())
                && subject.spaceId() == null;
    }
}
