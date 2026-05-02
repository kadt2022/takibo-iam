package com.takibo.identitycore.domain.catalogrbac;

import java.util.Collections;
import java.util.Set;
import java.util.stream.Collectors;

public interface TechnicalRbacCatalog {

    boolean isTechnicalRole(String roleCode);

    boolean isTechnicalGroup(String groupCode);

    Set<String> getPermissionsForRoleCode(String roleCode);

    Set<String> getRoleCodesForGroupCode(String groupCode);
}
