package com.takibo.identitycore.domain.catalogrbac;

import java.util.List;

public interface TechnicalRbacCatalog {

    List<TechnicalRole> getCanonicalRoles();

    List<TechnicalPermission> getCanonicalPermissions();

}
