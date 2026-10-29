package com.takibo.securitymanagement.domain.service.policy;

import static com.takibo.securitymanagement.domain.service.policy.PolicyConditions.isUuid;

/**
 * Route de la surface clients OAuth2 : /api/v1/orgs/{UUID}/spaces/{UUID}/clients
 * et toutes ses sous-routes ({id}/rotate-secret, ...). Le discriminant UUID suit
 * la même doctrine que la surface spaces TMS : les routes en codes lisibles ne
 * matchent jamais. Partagée par {@link OAuthClientPolicy}, qui gouverne la surface, et
 * {@link TmsSpacePolicy}, qui la lui laisse.
 */
record OAuthClientRoute(String orgId, String spaceId, Kind kind) {

    enum Kind {
        COLLECTION_CREATE,
        ROTATE_SECRET,
        UNKNOWN
    }

    static OAuthClientRoute parse(String path) {
        // ["", "api", "v1", "orgs", {orgId}, "spaces", {spaceId}, "clients", ...]
        String[] seg = path.split("/");
        boolean onSurface = seg.length >= 8
                && seg[0].isEmpty()
                && "api".equals(seg[1]) && "v1".equals(seg[2])
                && "orgs".equals(seg[3]) && "spaces".equals(seg[5])
                && "clients".equals(seg[7])
                && isUuid(seg[4]) && isUuid(seg[6]);
        if (!onSurface) {
            return null;
        }
        if (seg.length == 8) {
            return new OAuthClientRoute(seg[4], seg[6], Kind.COLLECTION_CREATE);
        }
        if (seg.length == 10 && isUuid(seg[8]) && "rotate-secret".equals(seg[9])) {
            return new OAuthClientRoute(seg[4], seg[6], Kind.ROTATE_SECRET);
        }
        return new OAuthClientRoute(seg[4], seg[6], Kind.UNKNOWN);
    }
}
