package com.takibo.identitycore.domain.repository;

import com.takibo.identitycore.domain.model.Role;
import com.takibo.identitycore.domain.vo.RoleId;
import com.takibo.identitycore.domain.vo.SpaceId;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RoleRepository {

    boolean existsBySpaceIdAndCode(SpaceId spaceId, String roleCode);

    Optional<UUID> findIdBySpaceIdAndCode(SpaceId spaceId, String roleCode);

    Optional<Role> findBySpaceIdAndCode(SpaceId spaceId, String code);

    Optional<Role> findById(RoleId id);

    List<Role> findBusinessRolesByOrgAndSpaceAndCodes(UUID orgId, UUID spaceId, List<String> codes);

    List<Role> findGovernanceRolesByOrgAndSpaceAndCodes(UUID orgId, UUID spaceId, List<String> codes);

    List<Role> findAllByOrgAndSpace(UUID orgId, UUID spaceId);

    Role save(Role role);
}
