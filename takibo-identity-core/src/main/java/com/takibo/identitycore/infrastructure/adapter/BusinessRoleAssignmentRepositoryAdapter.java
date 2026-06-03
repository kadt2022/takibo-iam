package com.takibo.identitycore.infrastructure.adapter;

import com.takibo.identitycore.domain.model.IdentityType;
import com.takibo.identitycore.domain.rbac.model.BusinessRoleAssignment;
import com.takibo.identitycore.domain.rbac.model.RoleSource;
import com.takibo.identitycore.domain.rbac.repository.BusinessRoleAssignmentRepository;
import com.takibo.identitycore.infrastructure.entity.RoleAssignmentEntity;
import com.takibo.identitycore.infrastructure.jpa.repository.JpaRoleAssignmentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class BusinessRoleAssignmentRepositoryAdapter implements BusinessRoleAssignmentRepository {

    private final JpaRoleAssignmentRepository jpa;

    @Override
    public boolean existsByOrgIdAndSpaceIdAndIdentityIdAndBusinessRoleId(
            UUID orgId, UUID spaceId, UUID identityId, UUID businessRoleId) {
        return jpa.existsByOrgIdAndSpaceIdAndIdentityTypeAndIdentityIdAndRoleSourceAndBusinessRoleId(
                orgId, spaceId, IdentityType.HUMAN.name(), identityId, RoleSource.BUSINESS, businessRoleId);
    }

    @Override
    @Transactional
    public void saveAll(List<BusinessRoleAssignment> assignments) {
        List<RoleAssignmentEntity> entities = assignments.stream()
                .map(this::toEntity)
                .toList();
        jpa.saveAllAndFlush(entities);
    }

    private RoleAssignmentEntity toEntity(BusinessRoleAssignment assignment) {
        return RoleAssignmentEntity.builder()
                .orgId(assignment.orgId())
                .spaceId(assignment.spaceId())
                .identityType(IdentityType.HUMAN.name())
                .identityId(assignment.identityId())
                .roleSource(RoleSource.BUSINESS)
                .businessRoleId(assignment.businessRoleId())
                .roleCode(null)
                .build();
    }
}
