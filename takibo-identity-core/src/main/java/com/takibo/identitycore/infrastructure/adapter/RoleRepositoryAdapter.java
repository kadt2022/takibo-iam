package com.takibo.identitycore.infrastructure.adapter;

import com.takibo.identitycore.domain.model.Role;
import com.takibo.identitycore.domain.model.RoleNature;
import com.takibo.identitycore.domain.repository.RoleRepository;
import com.takibo.identitycore.domain.vo.RoleId;
import com.takibo.identitycore.domain.vo.SpaceId;
import com.takibo.identitycore.infrastructure.entity.RoleEntity;
import com.takibo.identitycore.infrastructure.jpa.mapper.RoleJpaMapper;
import com.takibo.identitycore.infrastructure.jpa.repository.JpaRoleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class RoleRepositoryAdapter implements RoleRepository {

    private final JpaRoleRepository jpa;
    private final RoleJpaMapper roleJpaMapper;

    @Override
    public boolean existsBySpaceIdAndCode(SpaceId spaceId, String roleCode) {
        return jpa.existsBySpaceIdAndCode(spaceId.value(), roleCode);
    }

    @Override
    public Optional<UUID> findIdBySpaceIdAndCode(SpaceId spaceId, String roleCode) {
        return jpa.findIdBySpaceIdAndCode(spaceId.value(), roleCode);
    }

    @Override
    public Optional<Role> findById(RoleId id) {
        return jpa.findById(id.getValue()).map(roleJpaMapper::toDomain);
    }

    @Override
    public Optional<Role> findBySpaceIdAndCode(SpaceId spaceId, String code) {
        return jpa.findBySpaceIdAndCode(spaceId.value(), code)
                .map(roleJpaMapper::toDomain);
    }

    @Override
    public List<Role> findBusinessRolesByOrgAndSpaceAndCodes(UUID orgId, UUID spaceId, List<String> codes) {
        return jpa.findByOrgIdAndSpaceIdAndCodeInAndRoleNature(orgId, spaceId, codes, RoleNature.BUSINESS)
                .stream().map(roleJpaMapper::toDomain).toList();
    }

    @Override
    public List<Role> findGovernanceRolesByOrgAndSpaceAndCodes(UUID orgId, UUID spaceId, List<String> codes) {
        return jpa.findByOrgIdAndSpaceIdAndCodeInAndRoleNature(orgId, spaceId, codes, RoleNature.GOVERNANCE)
                .stream().map(roleJpaMapper::toDomain).toList();
    }

    @Override
    public Role save(Role role) {
        RoleEntity entity = roleJpaMapper.toEntity(role);
        jpa.save(entity);
        return role;
    }
}
