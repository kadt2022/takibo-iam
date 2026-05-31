package com.takibo.identitycore.infrastructure.jpa.repository;

import com.takibo.identitycore.domain.model.IdentityType;
import com.takibo.identitycore.domain.rbac.model.RoleSource;
import com.takibo.identitycore.infrastructure.entity.RoleAssignmentEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface JpaRoleAssignmentRepository extends JpaRepository<RoleAssignmentEntity, UUID> {

    List<RoleAssignmentEntity> findByOrgIdAndIdentityTypeAndIdentityId(
            UUID orgId, IdentityType identityType, UUID identityId);

    List<RoleAssignmentEntity> findByOrgIdAndSpaceIdAndIdentityTypeAndIdentityId(
            UUID orgId, UUID spaceId, IdentityType identityType, UUID identityId);

    List<RoleAssignmentEntity> findByOrgIdAndRoleCode(UUID orgId, String roleCode);

    boolean existsByOrgIdAndSpaceIdAndIdentityTypeAndIdentityIdAndRoleSourceAndBusinessRoleId(
            UUID orgId,
            UUID spaceId,
            String identityType,
            UUID identityId,
            RoleSource roleSource,
            UUID businessRoleId
    );
}
