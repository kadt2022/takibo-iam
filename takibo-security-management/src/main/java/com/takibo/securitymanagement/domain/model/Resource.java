package com.takibo.securitymanagement.domain.model;

import java.util.UUID;

public record Resource(
        String path,
        String orgId,
        String spaceId
) {

    /**
     * Ressource visée par une requête, lue dans son chemin selon le contrat d'API :
     * {@code /api/v1/orgs/{orgId}/...} porte l'organisation cible en UUID (RBAC-06).
     * Tout autre chemin (signup, /me, routes lisibles en codes, routes historiques)
     * ne désigne pas d'organisation ici. Le space n'est pas lu : sa frontière reste
     * portée par les règles de surface et les gardes applicatives.
     */
    public static Resource fromPath(String path) {
        return new Resource(path, organizationIdOf(path), null);
    }

    private static String organizationIdOf(String path) {
        if (path == null) {
            return null;
        }
        // ["", "api", "v1", "orgs", {orgId}, ...]
        String[] seg = path.split("/");
        if (seg.length < 5 || !seg[0].isEmpty()
                || !"api".equals(seg[1]) || !"v1".equals(seg[2]) || !"orgs".equals(seg[3])) {
            return null;
        }
        try {
            return UUID.fromString(seg[4]).toString();
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
