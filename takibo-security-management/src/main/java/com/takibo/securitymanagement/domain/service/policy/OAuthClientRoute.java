package com.takibo.securitymanagement.domain.service.policy;

import com.takibo.identitycore.domain.catalogrbac.TechnicalPermission;

import java.util.Optional;

/**
 * Route de la surface clients OAuth2 : /api/v1/orgs/{UUID}/spaces/{UUID}/clients et toutes
 * ses sous-routes ({id}/rotate-secret, ...). Le discriminant UUID suit la même doctrine que la
 * surface spaces TMS : les routes en codes lisibles ne matchent jamais. Partagée par
 * {@link OAuthClientPolicy}, qui gouverne la surface, et {@link TmsSpacePolicy}, qui la lui laisse.
 */
record OAuthClientRoute(String orgId, String spaceId, Kind kind) {

    enum Kind {
        COLLECTION_CREATE(TechnicalPermission.SPACE_CLIENTS_MANAGE),
        ROTATE_SECRET(TechnicalPermission.SPACE_CLIENTS_ROTATE_SECRET),
        UNKNOWN(TechnicalPermission.SPACE_CLIENTS_MANAGE);

        private final TechnicalPermission requiredPermission;

        Kind(TechnicalPermission requiredPermission) {
            this.requiredPermission = requiredPermission;
        }

        TechnicalPermission requiredPermission() {
            return requiredPermission;
        }
    }

    private static final int SPACE = 6;
    private static final int CLIENTS = 7;

    static Optional<OAuthClientRoute> parse(String path) {
        // ["", "api", "v1", "orgs", {orgId}, "spaces", {spaceId}, "clients", ...]
        return Optional.of(RequestPath.of(path))
                .filter(request -> request.isOrganizationRoute(CLIENTS + 1)
                        && request.segmentIs(5, "spaces")
                        && request.segmentIs(CLIENTS, "clients")
                        && request.hasUuidOrganization()
                        && request.isUuidAt(SPACE))
                .map(request -> new OAuthClientRoute(request.organization(), request.segment(SPACE), kindOf(request)));
    }

    private static Kind kindOf(RequestPath request) {
        if (request.size() == CLIENTS + 1) {
            return Kind.COLLECTION_CREATE;
        }
        boolean rotateSecret = request.size() == CLIENTS + 3
                && request.isUuidAt(CLIENTS + 1)
                && request.segmentIs(CLIENTS + 2, "rotate-secret");
        return rotateSecret ? Kind.ROTATE_SECRET : Kind.UNKNOWN;
    }
}
