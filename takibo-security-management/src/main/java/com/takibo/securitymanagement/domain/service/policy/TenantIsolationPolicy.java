package com.takibo.securitymanagement.domain.service.policy;

import com.takibo.securitymanagement.domain.model.Action;
import com.takibo.securitymanagement.domain.model.PolicyDecision;
import com.takibo.securitymanagement.domain.model.Resource;
import com.takibo.securitymanagement.domain.model.Subject;
import lombok.extern.slf4j.Slf4j;

import java.util.Optional;

import static com.takibo.securitymanagement.domain.service.policy.Decisions.POL_ORG_MISMATCH;
import static com.takibo.securitymanagement.domain.service.policy.Decisions.deny;
import static com.takibo.securitymanagement.domain.service.policy.RequestPath.sameUuid;

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
        return Optional.ofNullable(resource.orgId())
                .filter(targetOrg -> !sameUuid(targetOrg, subject.orgId()))
                .map(targetOrg -> {
                    log.warn("Tenant isolation violation: subject.orgId={}, resource.orgId={}",
                            subject.orgId(), targetOrg);
                    return deny(POL_ORG_MISMATCH, "User does not belong to target organization");
                });
    }
}
