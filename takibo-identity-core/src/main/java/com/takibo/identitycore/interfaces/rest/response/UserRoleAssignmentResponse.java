package com.takibo.identitycore.interfaces.rest.response;

import com.takibo.identitycore.application.rbac.catalog.model.CatalogNature;
import com.takibo.identitycore.application.rbac.catalog.model.CatalogOrigin;
import com.takibo.identitycore.domain.catalogrbac.TechnicalScope;

/**
 * Un rôle directement assigné à un user. {@code source=DIRECT} en PR #26 —
 * les rôles hérités par groupe viendront avec le RBAC read-side complet.
 */
public record UserRoleAssignmentResponse(
        String code,
        CatalogOrigin origin,
        CatalogNature nature,
        TechnicalScope scope,
        String source
) {}
