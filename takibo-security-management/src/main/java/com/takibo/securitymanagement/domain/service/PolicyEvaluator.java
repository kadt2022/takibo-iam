package com.takibo.securitymanagement.domain.service;

import com.takibo.securitymanagement.domain.model.Action;
import com.takibo.securitymanagement.domain.model.Effect;
import com.takibo.securitymanagement.domain.model.Environment;
import com.takibo.securitymanagement.domain.model.PolicyDecision;
import com.takibo.securitymanagement.domain.model.Resource;
import com.takibo.securitymanagement.domain.model.Subject;
import com.takibo.securitymanagement.domain.service.policy.CurrentUserSpacesPolicy;
import com.takibo.securitymanagement.domain.service.policy.OAuthClientPolicy;
import com.takibo.securitymanagement.domain.service.policy.OrgDashboardPolicy;
import com.takibo.securitymanagement.domain.service.policy.OrganizationSignupPolicy;
import com.takibo.securitymanagement.domain.service.policy.ReadableSpacePolicy;
import com.takibo.securitymanagement.domain.service.policy.ResourcePolicy;
import com.takibo.securitymanagement.domain.service.policy.TenantIsolationPolicy;
import com.takibo.securitymanagement.domain.service.policy.TmsSpacePolicy;
import com.takibo.securitymanagement.domain.service.policy.UngovernedTmsPolicy;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

/**
 * Politique déterministe (deny-wins) : chaîne de responsabilité de règles de surface.
 * <ol>
 *   <li>Isolation tenant : garde évaluée en premier, elle ne peut que refuser.</li>
 *   <li>Règles de surface, dans un ordre fixe : la première qui décide l'emporte.</li>
 *   <li>Aucune règle ne décide : PERMIT par défaut (POL_DEFAULT_ALLOW).</li>
 * </ol>
 */
@Service
public class PolicyEvaluator {

    private static final ResourcePolicy TENANT_ISOLATION = new TenantIsolationPolicy();

    // ORDONNANCEMENT : la première règle qui décide l'emporte.
    // - OAuthClientPolicy avant TmsSpacePolicy : TmsSpaceRoute classerait .../spaces/{uuid}/clients
    //   comme sous-route refusée pour tous ; TmsSpacePolicy écarte aussi elle-même cette surface.
    // - UngovernedTmsPolicy en dernier : verrou de toute route TMS qu'aucune règle ne gouverne.
    private static final List<ResourcePolicy> RESOURCE_POLICIES = List.of(
            new OrganizationSignupPolicy(),
            new CurrentUserSpacesPolicy(),
            new OrgDashboardPolicy(),
            new ReadableSpacePolicy(),
            new OAuthClientPolicy(),
            new TmsSpacePolicy(),
            new UngovernedTmsPolicy());

    public PolicyDecision evaluate(Subject subject,
                                   Resource resource,
                                   Action action,
                                   Environment env) {
        return TENANT_ISOLATION.evaluate(subject, resource, action)
                .or(() -> firstDecision(subject, resource, action))
                .orElseGet(PolicyEvaluator::defaultAllow);
    }

    private static PolicyDecision defaultAllow() {
        return PolicyDecision.builder()
                .effect(Effect.PERMIT)
                .policyId("POL_DEFAULT_ALLOW")
                .reason("No denying policy matched")
                .build();
    }

    /** Flux paresseux : les règles suivant la première qui décide ne sont pas évaluées. */
    private static Optional<PolicyDecision> firstDecision(Subject subject, Resource resource, Action action) {
        return RESOURCE_POLICIES.stream()
                .map(policy -> policy.evaluate(subject, resource, action))
                .flatMap(Optional::stream)
                .findFirst();
    }
}
