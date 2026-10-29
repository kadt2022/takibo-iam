package com.takibo.securitymanagement.domain.service.policy;

import com.takibo.identitycore.domain.catalogrbac.TechnicalPermission;
import com.takibo.securitymanagement.domain.model.Action;
import com.takibo.securitymanagement.domain.model.PolicyDecision;
import com.takibo.securitymanagement.domain.model.Resource;
import com.takibo.securitymanagement.domain.model.Subject;

import java.util.Optional;

import static com.takibo.securitymanagement.domain.service.policy.Decisions.ORG_MISMATCH_REASON;
import static com.takibo.securitymanagement.domain.service.policy.Decisions.POL_ORG_MISMATCH;
import static com.takibo.securitymanagement.domain.service.policy.Decisions.Requirement.require;
import static com.takibo.securitymanagement.domain.service.policy.Decisions.firstUnmet;
import static com.takibo.securitymanagement.domain.service.policy.Decisions.permit;
import static com.takibo.securitymanagement.domain.service.policy.Subjects.SCOPE_SPACE;
import static com.takibo.securitymanagement.domain.service.policy.Subjects.hasPermission;
import static com.takibo.securitymanagement.domain.service.policy.Subjects.sameIgnoringCase;

/**
 * /api/v1/orgs/{UUID}/spaces/{UUID}/clients[...] (management des clients OAuth2)
 * Règle POL_OAUTH_CLIENT_ADMIN_REQUIRED : créer un client machine ou faire tourner
 * son secret est l'acte le plus sensible du tenant (le secret sort en clair).
 * Exigences cumulatives : sujet HUMAIN, token de portée SPACE (org_id ET space_id
 * présents), org et space du token == org et space du chemin, et la permission
 * P_SPACE_CLIENTS_MANAGE (création) ou P_SPACE_CLIENTS_ROTATE_SECRET (rotation).
 * Un token ORGANIZATION (sans space_id) est REFUSÉ : la surface reste volontairement
 * inutilisable pour lui jusqu'à l'échange ORG->SPACE (IAM 34) — fail-closed assumé.
 * Cette règle gouverne TOUTE la surface clients : rien n'y retombe jamais sur
 * POL_DEFAULT_ALLOW.
 */
public final class OAuthClientPolicy implements ResourcePolicy {

    private static final String ADMIN_REQUIRED = "POL_OAUTH_CLIENT_ADMIN_REQUIRED";

    @Override
    public Optional<PolicyDecision> evaluate(Subject subject, Resource resource, Action action) {
        return OAuthClientRoute.parse(resource.path()).map(route -> decide(subject, route, action));
    }

    private static PolicyDecision decide(Subject subject, OAuthClientRoute route, Action action) {
        TechnicalPermission required = route.kind().requiredPermission();
        return firstUnmet(
                require(subject::isHuman, ADMIN_REQUIRED,
                        "Only a HUMAN subject may manage OAuth clients"),
                require(() -> sameIgnoringCase(subject.orgId(), route.orgId()), POL_ORG_MISMATCH,
                        ORG_MISMATCH_REASON),
                require(() -> SCOPE_SPACE.equals(subject.scopeLevel()), ADMIN_REQUIRED,
                        "A SPACE-scoped token is required"),
                require(() -> sameIgnoringCase(subject.spaceId(), route.spaceId()), ADMIN_REQUIRED,
                        "A SPACE-scoped token designating the target space is required to manage OAuth clients"),
                require(() -> route.kind() != OAuthClientRoute.Kind.UNKNOWN, "POL_OAUTH_CLIENT_ROUTE_NOT_GOVERNED",
                        "No policy governs this OAuth client route yet"),
                require(() -> action == Action.CREATE, "POL_OAUTH_CLIENT_ACTION_NOT_SUPPORTED",
                        "Only CREATE is governed on the OAuth clients surface"),
                require(() -> hasPermission(subject, required), ADMIN_REQUIRED,
                        required.code() + " required to manage OAuth clients"))
                .orElseGet(() -> permit(ADMIN_REQUIRED,
                        required.code() + " holder bound to the target space may manage OAuth clients"));
    }
}
