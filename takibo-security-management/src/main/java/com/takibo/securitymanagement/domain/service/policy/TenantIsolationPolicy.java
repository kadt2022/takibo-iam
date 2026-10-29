package com.takibo.securitymanagement.domain.service.policy;

import com.takibo.securitymanagement.domain.model.Action;
import com.takibo.securitymanagement.domain.model.PolicyDecision;
import com.takibo.securitymanagement.domain.model.Resource;
import com.takibo.securitymanagement.domain.model.Subject;
import lombok.extern.slf4j.Slf4j;

import java.util.Optional;

import static com.takibo.securitymanagement.domain.service.policy.PolicyConditions.sameUuid;
import static com.takibo.securitymanagement.domain.service.policy.PolicyDecisions.POL_ORG_MISMATCH;
import static com.takibo.securitymanagement.domain.service.policy.PolicyDecisions.deny;

/**
 * Isolation tenant (simple org-level pour v1), garde évaluée avant toute règle de surface.
 * Contrat RBAC-06 : une ressource qui désigne une organisation exige
 * token.org_id == orgId. Un token sans organisation (PLATFORM) est refusé tant que
 * l'intervention plateforme n'est pas explicitement située (RBAC-08).
 * Ne rend jamais de PERMIT : sans violation, la décision revient aux règles de surface.
 */
@Slf4j
public final class TenantIsolationPolicy implements ResourcePolicy {

    @Override
    public Optional<PolicyDecision> evaluate(Subject subject, Resource resource, Action action) {
        if (resource.orgId() != null && !sameUuid(resource.orgId(), subject.orgId())) {

            log.warn("Tenant isolation violation: subject.orgId={}, resource.orgId={}",
                    subject.orgId(), resource.orgId());

            return deny(POL_ORG_MISMATCH, "User does not belong to target organization");
        }
        return Optional.empty();
    }
}
