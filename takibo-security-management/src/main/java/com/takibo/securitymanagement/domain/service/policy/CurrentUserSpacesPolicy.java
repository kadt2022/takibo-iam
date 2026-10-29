package com.takibo.securitymanagement.domain.service.policy;

import com.takibo.securitymanagement.domain.model.Action;
import com.takibo.securitymanagement.domain.model.PolicyDecision;
import com.takibo.securitymanagement.domain.model.Resource;
import com.takibo.securitymanagement.domain.model.Subject;

import java.util.Optional;

import static com.takibo.securitymanagement.domain.service.policy.PolicyDecisions.deny;
import static com.takibo.securitymanagement.domain.service.policy.PolicyDecisions.permit;

/**
 * /api/v1/me/spaces (IAM 32) : surface personnelle, sans rôle, mais strictement
 * réservée à un humain authentifié au niveau ORGANIZATION avec org_id/account_id.
 */
public final class CurrentUserSpacesPolicy implements ResourcePolicy {

    private static final String POLICY_ID = "POL_MY_SPACES_ORG_HUMAN_REQUIRED";

    @Override
    public Optional<PolicyDecision> evaluate(Subject subject, Resource resource, Action action) {
        if (!"/api/v1/me/spaces".equals(resource.path())) {
            return Optional.empty();
        }
        if (action != Action.READ
                || !Subject.TYPE_HUMAN.equals(subject.subjectType())
                || !"ORGANIZATION".equals(subject.scopeLevel())
                || subject.orgId() == null
                || subject.orgId().isBlank()
                || subject.accountId() == null
                || subject.accountId().isBlank()
                || subject.spaceId() != null) {
            return deny(POLICY_ID,
                    "Organization-scoped human account token required to list accessible spaces");
        }
        return permit(POLICY_ID,
                "Organization-scoped human account token may list its own accessible spaces");
    }
}
