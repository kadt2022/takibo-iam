package com.takibo.identitycore.interfaces.rest.response;

import com.takibo.identitycore.application.rbac.catalog.model.CatalogNature;
import com.takibo.identitycore.application.rbac.catalog.model.CatalogOrigin;
import com.takibo.identitycore.domain.catalogrbac.AuthorityPlan;

import java.util.List;

/**
 * Vue catalogue d'un rôle. {@code editable} est faux pour tout élément TECHNICAL ;
 * {@code assignable} reflète ce que la plateforme sait assigner aujourd'hui
 * (assignation technique via provisioning) — aucune assignation n'est exposée en PR #25.
 */
public record RoleCatalogResponse(
        String code,
        String name,
        String description,
        CatalogOrigin origin,
        CatalogNature nature,
        AuthorityPlan scope,
        boolean editable,
        boolean assignable,
        List<String> permissions
) {}
