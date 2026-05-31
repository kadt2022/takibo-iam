package com.takibo.identitycore.infrastructure.jpa.repository;

import com.takibo.identitycore.domain.model.IdentityType;
import com.takibo.identitycore.domain.rbac.model.RoleSource;
import com.takibo.identitycore.infrastructure.entity.RoleAssignmentEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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

    @Modifying
    @Query(value = """
            INSERT INTO role_assignments (
                id,
                org_id,
                space_id,
                identity_type,
                identity_id,
                role_code,
                role_source,
                business_role_id,
                created_at,
                updated_at
            )
            VALUES (
                :id,
                :orgId,
                :spaceId,
                :identityType,
                :identityId,
                NULL,
                'BUSINESS',
                :businessRoleId,
                CURRENT_TIMESTAMP,
                CURRENT_TIMESTAMP
            )
            ON CONFLICT (org_id, space_id, identity_type, identity_id, business_role_id)
            WHERE space_id IS NOT NULL
              AND role_source = 'BUSINESS'
            DO NOTHING
            """, nativeQuery = true)
    int insertBusinessRoleAssignmentIfAbsent(
            @Param("id") UUID id,
            @Param("orgId") UUID orgId,
            @Param("spaceId") UUID spaceId,
            @Param("identityType") String identityType,
            @Param("identityId") UUID identityId,
            @Param("businessRoleId") UUID businessRoleId
    );
}
