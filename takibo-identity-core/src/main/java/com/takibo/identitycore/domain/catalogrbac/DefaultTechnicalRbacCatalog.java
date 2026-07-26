package com.takibo.identitycore.domain.catalogrbac;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class DefaultTechnicalRbacCatalog implements TechnicalRbacCatalog {

    @Override
    public List<TechnicalRole> getCanonicalRoles() {
        return TechnicalRole.canonicalValues();
    }

    @Override
    public List<TechnicalPermission> getCanonicalPermissions() {
        return List.of(TechnicalPermission.values());
    }

    @Override
    public boolean isTechnicalRole(String roleCode) {
        return TechnicalRole.fromCode(roleCode).isPresent();
    }

    @Override
    public boolean isTechnicalGroup(String groupCode) {
        return TechnicalGroup.fromCode(groupCode).isPresent();
    }

    @Override
    public Set<String> getPermissionsForRoleCode(String roleCode) {
        return TechnicalRole.fromCode(roleCode)
                .map(role -> role.permissions()
                        .stream()
                        .map(TechnicalGroup.TechnicalPermission::code)
                        .collect(Collectors.toUnmodifiableSet()))
                .orElse(Collections.emptySet());
    }

    @Override
    public Set<String> getRoleCodesForGroupCode(String groupCode) {
        return TechnicalGroup.fromCode(groupCode)
                .map(group -> group.roles()
                        .stream()
                        .map(TechnicalRole::code)
                        .collect(Collectors.toUnmodifiableSet()))
                .orElse(Collections.emptySet());
    }
}
