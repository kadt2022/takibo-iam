package com.takibo.identitycore.interfaces.rest.response;

import com.takibo.identitycore.application.rbac.catalog.model.CatalogNature;
import com.takibo.identitycore.application.rbac.catalog.model.CatalogOrigin;
import com.takibo.identitycore.domain.catalogrbac.TechnicalScope;

import java.util.List;

/**
 * Vue catalogue d'un groupe. {@code roles} liste les codes de rôles attachés —
 * renseignée pour les groupes techniques (liens définis dans le code) ; les liens
 * group→roles persistés en base seront exposés dans une PR ultérieure.
 */
public record GroupCatalogResponse(
        String code,
        String name,
        String description,
        CatalogOrigin origin,
        CatalogNature nature,
        TechnicalScope scope,
        boolean editable,
        List<String> roles
) {}
