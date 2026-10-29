package com.takibo.securitymanagement.domain.service.policy;

import com.takibo.identitycore.domain.catalogrbac.TechnicalPermission;
import com.takibo.securitymanagement.domain.model.Action;
import com.takibo.securitymanagement.domain.model.PolicyDecision;
import com.takibo.securitymanagement.domain.model.Resource;
import com.takibo.securitymanagement.domain.model.Subject;

import java.util.Optional;

import static com.takibo.securitymanagement.domain.service.policy.PolicyConditions.hasPermission;
import static com.takibo.securitymanagement.domain.service.policy.PolicyDecisions.POL_ORG_MISMATCH;
import static com.takibo.securitymanagement.domain.service.policy.PolicyDecisions.deny;
import static com.takibo.securitymanagement.domain.service.policy.PolicyDecisions.permit;

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

    @Override
    public Optional<PolicyDecision> evaluate(Subject subject, Resource resource, Action action) {
        OAuthClientRoute route = OAuthClientRoute.parse(resource.path());
        if (route == null) {
            return Optional.empty();
        }

        if (!subject.isHuman()) {
            return deny("POL_OAUTH_CLIENT_ADMIN_REQUIRED",
                    "Only a HUMAN subject may manage OAuth clients");
        }
        if (subject.orgId() == null || !subject.orgId().equalsIgnoreCase(route.orgId())) {
            return deny(POL_ORG_MISMATCH,
                    "Token organization does not match the target organization");
        }
        if (!"SPACE".equals(subject.scopeLevel())) {
            return deny("POL_OAUTH_CLIENT_ADMIN_REQUIRED",
                    "A SPACE-scoped token is required");
        }
        if (subject.spaceId() == null || !subject.spaceId().equalsIgnoreCase(route.spaceId())) {
            return deny("POL_OAUTH_CLIENT_ADMIN_REQUIRED",
                    "A SPACE-scoped token designating the target space is required to manage OAuth clients");
        }
        if (route.kind() == OAuthClientRoute.Kind.UNKNOWN) {
            return deny("POL_OAUTH_CLIENT_ROUTE_NOT_GOVERNED",
                    "No policy governs this OAuth client route yet");
        }
        if (action != Action.CREATE) {
            return deny("POL_OAUTH_CLIENT_ACTION_NOT_SUPPORTED",
                    "Only CREATE is governed on the OAuth clients surface");
        }
        TechnicalPermission required = route.kind() == OAuthClientRoute.Kind.ROTATE_SECRET
                ? TechnicalPermission.SPACE_CLIENTS_ROTATE_SECRET
                : TechnicalPermission.SPACE_CLIENTS_MANAGE;
        if (!hasPermission(subject, required)) {
            return deny("POL_OAUTH_CLIENT_ADMIN_REQUIRED",
                    required.code() + " required to manage OAuth clients");
        }
        return permit("POL_OAUTH_CLIENT_ADMIN_REQUIRED",
                required.code() + " holder bound to the target space may manage OAuth clients");
    }
}
