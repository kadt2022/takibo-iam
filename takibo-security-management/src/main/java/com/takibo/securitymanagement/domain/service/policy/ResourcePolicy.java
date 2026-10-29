package com.takibo.securitymanagement.domain.service.policy;

import com.takibo.securitymanagement.domain.model.Action;
import com.takibo.securitymanagement.domain.model.PolicyDecision;
import com.takibo.securitymanagement.domain.model.Resource;
import com.takibo.securitymanagement.domain.model.Subject;

import java.util.Optional;

/**
 * Une règle d'autorisation d'une surface. Elle rend une décision explicite (PERMIT ou DENY)
 * pour les ressources qu'elle gouverne, et {@link Optional#empty()} pour toutes les autres :
 * l'absence de décision n'est pas une autorisation, c'est la règle suivante qui est consultée.
 */
public interface ResourcePolicy {

    Optional<PolicyDecision> evaluate(Subject subject, Resource resource, Action action);
}
