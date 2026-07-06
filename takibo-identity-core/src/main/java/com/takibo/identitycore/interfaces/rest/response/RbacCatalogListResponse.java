package com.takibo.identitycore.interfaces.rest.response;

import java.util.List;

/**
 * Enveloppe de liste du catalogue RBAC. Pas de pagination : le catalogue d'un space
 * est borné (catalogue technique + quelques éléments tenant), trié par code.
 */
public record RbacCatalogListResponse<T>(
        List<T> items,
        int total
) {}
