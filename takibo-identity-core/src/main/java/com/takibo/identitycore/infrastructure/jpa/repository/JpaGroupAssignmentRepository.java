package com.takibo.identitycore.infrastructure.jpa.repository;

import com.takibo.identitycore.domain.model.IdentityType;
import com.takibo.identitycore.infrastructure.entity.GroupAssignmentEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface JpaGroupAssignmentRepository extends JpaRepository<GroupAssignmentEntity, UUID> {

    List<GroupAssignmentEntity> findByOrgIdAndIdentityTypeAndIdentityId(
            UUID orgId, IdentityType identityType, UUID identityId);

    List<GroupAssignmentEntity> findByOrgIdAndSpaceIdAndIdentityTypeAndIdentityId(
            UUID orgId, UUID spaceId, IdentityType identityType, UUID identityId);
}
