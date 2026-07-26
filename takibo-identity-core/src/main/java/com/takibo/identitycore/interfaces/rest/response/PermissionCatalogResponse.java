package com.takibo.identitycore.interfaces.rest.response;

import com.takibo.identitycore.application.rbac.catalog.model.CatalogNature;
import com.takibo.identitycore.application.rbac.catalog.model.CatalogOrigin;
import com.takibo.identitycore.domain.catalogrbac.AuthorityPlan;

/**
 * Vue catalogue d'une permission. En PR #25, seules les permissions techniques
 * (enum du catalogue plateforme) existent — la table permissions n'a aucun writer.
 */
public record PermissionCatalogResponse(
        String code,
        String description,
        CatalogOrigin origin,
        CatalogNature nature,
        AuthorityPlan scope,
        boolean editable
) {}
