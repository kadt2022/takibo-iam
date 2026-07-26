package com.takibo.identitycore.application.rbac.catalog.mapper;

import com.takibo.identitycore.application.rbac.catalog.model.CatalogNature;
import com.takibo.identitycore.application.rbac.catalog.model.CatalogOrigin;
import com.takibo.identitycore.domain.catalogrbac.AuthorityPlan;
import com.takibo.identitycore.domain.catalogrbac.TechnicalGroup;
import com.takibo.identitycore.domain.catalogrbac.TechnicalRole;
import com.takibo.identitycore.domain.model.Group;
import com.takibo.identitycore.domain.model.Role;
import com.takibo.identitycore.domain.model.RoleNature;
import com.takibo.identitycore.interfaces.rest.response.GroupCatalogResponse;
import com.takibo.identitycore.interfaces.rest.response.PermissionCatalogResponse;
import com.takibo.identitycore.interfaces.rest.response.RoleCatalogResponse;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Présentation du catalogue RBAC composite. Mapper écrit main, volontairement pas
 * MapStruct : rien ne se mappe 1:1 — les champs {@code origin}/{@code nature}/
 * {@code editable}/{@code assignable} sont des décisions doctrinales (PR #25),
 * pas des copies de champs source.
 * <ul>
 *   <li>TECHNICAL : jamais éditable par tenant, assignable (provisioning + PR #26).</li>
 *   <li>DATABASE : éditable ; GOVERNANCE assignable (PR #26), BUSINESS pas encore.</li>
 * </ul>
 */
@Component
public class RbacCatalogMapper {

    public RoleCatalogResponse toResponse(TechnicalRole role) {
        return new RoleCatalogResponse(
                role.code(),
                role.displayName(),
                role.description(),
                CatalogOrigin.TECHNICAL,
                CatalogNature.TECHNICAL,
                role.plan(),
                false,
                role.assignable(),
                role.permissions().stream()
                        .map(TechnicalGroup.TechnicalPermission::code)
                        .sorted()
                        .toList());
    }

    public RoleCatalogResponse toResponse(Role role) {
        return new RoleCatalogResponse(
                role.getCode(),
                role.getName(),
                role.getDescription(),
                CatalogOrigin.DATABASE,
                CatalogNature.valueOf(role.getNature().name()),
                AuthorityPlan.SPACE,
                true,
                // GOVERNANCE est assignable depuis PR #26 ; BUSINESS attend sa propre PR.
                role.getNature() == RoleNature.GOVERNANCE,
                List.of());
    }

    public GroupCatalogResponse toResponse(TechnicalGroup group) {
        return new GroupCatalogResponse(
                group.code(),
                group.name(),
                null,
                CatalogOrigin.TECHNICAL,
                CatalogNature.TECHNICAL,
                group.plan(),
                false,
                group.roles().stream().map(TechnicalRole::code).sorted().toList());
    }

    public GroupCatalogResponse toResponse(Group group) {
        return new GroupCatalogResponse(
                group.getCode(),
                group.getName(),
                group.getDescription(),
                CatalogOrigin.DATABASE,
                CatalogNature.valueOf(group.getNature().name()),
                AuthorityPlan.SPACE,
                true,
                // Les liens group->roles persistés seront exposés dans une PR ultérieure.
                List.of());
    }

    public PermissionCatalogResponse toResponse(TechnicalGroup.TechnicalPermission permission) {
        return new PermissionCatalogResponse(
                permission.code(),
                permission.description(),
                CatalogOrigin.TECHNICAL,
                CatalogNature.TECHNICAL,
                permission.plan(),
                false);
    }
}
