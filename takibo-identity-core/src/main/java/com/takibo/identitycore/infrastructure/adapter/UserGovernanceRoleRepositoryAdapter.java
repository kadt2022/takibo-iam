package com.takibo.identitycore.infrastructure.adapter;

import com.takibo.identitycore.domain.rbac.model.UserGovernanceRoleAssignment;
import com.takibo.identitycore.domain.repository.UserGovernanceRoleRepository;
import com.takibo.identitycore.infrastructure.entity.UserRoleEntity;
import com.takibo.identitycore.infrastructure.jpa.repository.JpaUserRoleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class UserGovernanceRoleRepositoryAdapter implements UserGovernanceRoleRepository {

    private final JpaUserRoleRepository jpa;

    @Override
    public boolean existsByOrgIdAndSpaceIdAndUserIdAndGovernanceRoleId(
            UUID orgId, UUID spaceId, UUID userId, UUID governanceRoleId) {
        return jpa.existsByOrgIdAndSpaceIdAndUserIdAndRoleId(orgId, spaceId, userId, governanceRoleId);
    }

    @Override
    @Transactional
    public void saveAll(List<UserGovernanceRoleAssignment> assignments) {
        List<UserRoleEntity> entities = assignments.stream()
                .map(this::toEntity)
                .toList();
        try {
            jpa.saveAllAndFlush(entities);
        } catch (DataIntegrityViolationException ex) {
            boolean allNowExist = entities.stream().allMatch(e ->
                    jpa.existsByOrgIdAndSpaceIdAndUserIdAndRoleId(
                            e.getOrgId(), e.getSpaceId(), e.getUserId(), e.getRoleId()));
            if (!allNowExist) {
                log.warn("Failed to persist governance user_roles — not all assignments exist after conflict", ex);
                throw ex;
            }
            log.debug("Idempotent governance user_roles — concurrent insert already committed");
        }
    }

    private UserRoleEntity toEntity(UserGovernanceRoleAssignment assignment) {
        return UserRoleEntity.builder()
                .orgId(assignment.orgId())
                .spaceId(assignment.spaceId())
                .userId(assignment.userId())
                .roleId(assignment.governanceRoleId())
                .assignedAt(assignment.assignedAt())
                .build();
    }
}
