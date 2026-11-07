package com.takibo.identitycore.domain.catalogrbac;

import java.util.List;

public class DefaultTechnicalRbacCatalog implements TechnicalRbacCatalog {

    @Override
    public List<TechnicalRole> getCanonicalRoles() {
        return TechnicalRole.canonicalValues();
    }

    @Override
    public List<TechnicalPermission> getCanonicalPermissions() {
        return List.of(TechnicalPermission.values());
    }
}
