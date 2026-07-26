package com.takibo.identitycore.domain.catalogrbac;

import java.util.List;
import java.util.Set;

public interface TechnicalRbacCatalog {

    List<TechnicalRole> getCanonicalRoles();

    List<TechnicalPermission> getCanonicalPermissions();

    boolean isTechnicalRole(String roleCode);

    boolean isTechnicalGroup(String groupCode);

    Set<String> getPermissionsForRoleCode(String roleCode);

    Set<String> getRoleCodesForGroupCode(String groupCode);
}
