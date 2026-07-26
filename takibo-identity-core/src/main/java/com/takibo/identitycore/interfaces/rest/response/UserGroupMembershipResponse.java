package com.takibo.identitycore.interfaces.rest.response;

import com.takibo.identitycore.application.rbac.catalog.model.CatalogNature;
import com.takibo.identitycore.application.rbac.catalog.model.CatalogOrigin;
import com.takibo.identitycore.domain.catalogrbac.AuthorityPlan;

/** Un membership direct d'un user dans un groupe. */
public record UserGroupMembershipResponse(
        String code,
        CatalogOrigin origin,
        CatalogNature nature,
        AuthorityPlan scope,
        String source
) {}
