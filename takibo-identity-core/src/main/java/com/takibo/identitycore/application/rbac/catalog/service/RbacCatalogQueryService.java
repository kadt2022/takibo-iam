package com.takibo.identitycore.application.rbac.catalog.service;

import com.takibo.identitycore.application.rbac.catalog.mapper.RbacCatalogMapper;
import com.takibo.identitycore.application.rbac.catalog.port.in.RbacCatalogQueryCase;
import com.takibo.identitycore.domain.catalogrbac.TechnicalGroup;
import com.takibo.identitycore.domain.catalogrbac.TechnicalRole;
import com.takibo.identitycore.domain.catalogrbac.TechnicalScope;
import com.takibo.identitycore.domain.exception.GroupNotFoundException;
import com.takibo.identitycore.domain.exception.PermissionNotFoundException;
import com.takibo.identitycore.domain.exception.RoleNotFoundException;
import com.takibo.identitycore.domain.repository.GroupRepository;
import com.takibo.identitycore.domain.repository.RoleRepository;
import com.takibo.identitycore.domain.vo.SpaceId;
import com.takibo.identitycore.integration.security.SpaceBoundaryGuard;
import com.takibo.identitycore.integration.space.SpaceContextVerifier;
import com.takibo.identitycore.integration.space.port.ResolvedSpaceKey;
import com.takibo.identitycore.interfaces.rest.response.GroupCatalogResponse;
import com.takibo.identitycore.interfaces.rest.response.PermissionCatalogResponse;
import com.takibo.identitycore.interfaces.rest.response.RbacCatalogListResponse;
import com.takibo.identitycore.interfaces.rest.response.RoleCatalogResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Stream;

/**
 * Catalogue RBAC composite d'un space : le catalogue technique TAKIBO (enums, nature
 * TECHNICAL) filtré par scope, fusionné avec les rôles/groupes tenant persistés en base
 * (natures GOVERNANCE/BUSINESS). Jamais un simple SELECT.
 * <p>
 * Catalogue situé : les scopes SYSTEM (rôles plateforme) et USER (R_SELF) ne sont pas
 * exposés aux tenants — un code hors frontière N'EXISTE PAS (404, anti-énumération).
 * <p>
 * Frontière identique au read-side users : space actif + token situé sur l'org/space
 * résolus ; le rôle admin tenant est exigé par la policy (TSM), pas ici.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RbacCatalogQueryService implements RbacCatalogQueryCase {

    /** Seuls les scopes assignables dans la frontière d'un tenant sont racontés. */
    private static final Set<TechnicalScope> TENANT_VISIBLE_SCOPES =
            EnumSet.of(TechnicalScope.ORGANIZATION, TechnicalScope.SPACE);

    private final SpaceContextVerifier spaceContextVerifier;
    private final SpaceBoundaryGuard spaceBoundaryGuard;
    private final RoleRepository roleRepository;
    private final GroupRepository groupRepository;
    private final RbacCatalogMapper mapper;

    @Override
    public RbacCatalogListResponse<RoleCatalogResponse> listRoles(ResolvedSpaceKey key) {
        guard(key);

        // TreeMap : tri par code, et le catalogue technique fait autorité en cas de
        // collision de code avec une ligne tenant. Les lignes DB portant un code
        // technique caché (migrations seedées : R_TAKIBO_PLATFORM_*, R_SELF) sont
        // filtrées — la frontière du catalogue ne dépend pas du contenu de la base.
        Map<String, RoleCatalogResponse> byCode = new TreeMap<>();
        roleRepository.findAllByOrgAndSpace(key.orgId(), key.spaceId()).stream()
                .filter(role -> !isHiddenTechnicalRoleCode(role.getCode()))
                .forEach(role -> byCode.put(role.getCode(), mapper.toResponse(role)));
        tenantVisibleTechnicalRoles()
                .forEach(role -> byCode.put(role.code(), mapper.toResponse(role)));

        return new RbacCatalogListResponse<>(List.copyOf(byCode.values()), byCode.size());
    }

    @Override
    public RoleCatalogResponse getRole(ResolvedSpaceKey key, String roleCode) {
        guard(key);

        Optional<TechnicalRole> technical = TechnicalRole.fromCode(roleCode)
                .filter(role -> TENANT_VISIBLE_SCOPES.contains(role.scope()));
        if (technical.isPresent()) {
            return mapper.toResponse(technical.get());
        }

        // Un code technique caché n'existe pas pour les tenants, même si une ligne
        // seedée traîne en base : jamais de fallback DB pour ces codes.
        if (isHiddenTechnicalRoleCode(roleCode)) {
            throw new RoleNotFoundException("Role not found in this space: " + roleCode);
        }

        return roleRepository.findBySpaceIdAndCode(SpaceId.of(key.spaceId()), roleCode)
                .map(mapper::toResponse)
                .orElseThrow(() -> new RoleNotFoundException("Role not found in this space: " + roleCode));
    }

    @Override
    public RbacCatalogListResponse<GroupCatalogResponse> listGroups(ResolvedSpaceKey key) {
        guard(key);

        Map<String, GroupCatalogResponse> byCode = new TreeMap<>();
        groupRepository.findAllByOrgAndSpace(key.orgId(), key.spaceId()).stream()
                .filter(group -> !isHiddenTechnicalGroupCode(group.getCode()))
                .forEach(group -> byCode.put(group.getCode(), mapper.toResponse(group)));
        tenantVisibleTechnicalGroups()
                .forEach(group -> byCode.put(group.code(), mapper.toResponse(group)));

        return new RbacCatalogListResponse<>(List.copyOf(byCode.values()), byCode.size());
    }

    @Override
    public GroupCatalogResponse getGroup(ResolvedSpaceKey key, String groupCode) {
        guard(key);

        Optional<TechnicalGroup> technical = TechnicalGroup.fromCode(groupCode)
                .filter(group -> TENANT_VISIBLE_SCOPES.contains(group.scope()));
        if (technical.isPresent()) {
            return mapper.toResponse(technical.get());
        }

        if (isHiddenTechnicalGroupCode(groupCode)) {
            throw new GroupNotFoundException("Group not found in this space: " + groupCode);
        }

        return groupRepository.findBySpaceIdAndCode(SpaceId.of(key.spaceId()), groupCode)
                .map(mapper::toResponse)
                .orElseThrow(() -> new GroupNotFoundException("Group not found in this space: " + groupCode));
    }

    @Override
    public RbacCatalogListResponse<PermissionCatalogResponse> listPermissions(ResolvedSpaceKey key) {
        guard(key);

        List<PermissionCatalogResponse> items = tenantVisibleTechnicalPermissions()
                .map(mapper::toResponse)
                .sorted(Comparator.comparing(PermissionCatalogResponse::code))
                .toList();

        return new RbacCatalogListResponse<>(items, items.size());
    }

    @Override
    public PermissionCatalogResponse getPermission(ResolvedSpaceKey key, String permissionCode) {
        guard(key);

        return TechnicalGroup.TechnicalPermission.fromCode(permissionCode)
                .filter(permission -> TENANT_VISIBLE_SCOPES.contains(permission.scope()))
                .map(mapper::toResponse)
                .orElseThrow(() -> new PermissionNotFoundException(
                        "Permission not found in this space: " + permissionCode));
    }

    private void guard(ResolvedSpaceKey key) {
        spaceContextVerifier.validateSpaceContext(key.spaceId());
        spaceBoundaryGuard.assertTokenMatches(key);
    }

    /** Code du catalogue technique dont le scope n'est pas raconté aux tenants. */
    private static boolean isHiddenTechnicalRoleCode(String code) {
        return TechnicalRole.fromCode(code)
                .map(role -> !TENANT_VISIBLE_SCOPES.contains(role.scope()))
                .orElse(false);
    }

    private static boolean isHiddenTechnicalGroupCode(String code) {
        return TechnicalGroup.fromCode(code)
                .map(group -> !TENANT_VISIBLE_SCOPES.contains(group.scope()))
                .orElse(false);
    }

    private Stream<TechnicalRole> tenantVisibleTechnicalRoles() {
        return Arrays.stream(TechnicalRole.values())
                .filter(role -> TENANT_VISIBLE_SCOPES.contains(role.scope()));
    }

    private Stream<TechnicalGroup> tenantVisibleTechnicalGroups() {
        return Arrays.stream(TechnicalGroup.values())
                .filter(group -> TENANT_VISIBLE_SCOPES.contains(group.scope()));
    }

    private Stream<TechnicalGroup.TechnicalPermission> tenantVisibleTechnicalPermissions() {
        return Arrays.stream(TechnicalGroup.TechnicalPermission.values())
                .filter(permission -> TENANT_VISIBLE_SCOPES.contains(permission.scope()));
    }
}
