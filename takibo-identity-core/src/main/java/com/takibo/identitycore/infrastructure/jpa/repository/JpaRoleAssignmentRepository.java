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

    List<RoleAssignmentEntity> findByOrgIdAndIdentityId(UUID orgId, UUID identityId);

    boolean existsByOrgIdAndSpaceIdAndIdentityTypeAndIdentityIdAndRoleSourceAndBusinessRoleId(
            UUID orgId,
            UUID spaceId,
            String identityType,
            UUID identityId,
            RoleSource roleSource,
            UUID businessRoleId
    );

    /** Assignations par code (TECHNICAL/GOVERNANCE) d'un account, visibles sur le space. */
    @Query("""
        select a from RoleAssignmentEntity a
        where a.orgId = :orgId
          and a.identityType = 'ACCOUNT'
          and a.identityId = :accountId
          and a.roleCode is not null
          and (a.spaceId = :spaceId or a.spaceId is null)
        """)
    List<RoleAssignmentEntity> findDirectByOrgAndSpaceAndAccount(@Param("orgId") UUID orgId,
                                                                 @Param("spaceId") UUID spaceId,
                                                                 @Param("accountId") UUID accountId);

    @Query("""
        select (count(a) > 0) from RoleAssignmentEntity a
        where a.orgId = :orgId
          and a.identityType = 'ACCOUNT'
          and a.identityId = :accountId
          and a.roleCode = :roleCode
          and (a.spaceId = :spaceId or a.spaceId is null)
        """)
    boolean existsDirectAssignment(@Param("orgId") UUID orgId,
                                   @Param("spaceId") UUID spaceId,
                                   @Param("accountId") UUID accountId,
                                   @Param("roleCode") String roleCode);

    @Modifying
    @Query("""
        delete from RoleAssignmentEntity a
        where a.orgId = :orgId
          and a.spaceId = :spaceId
          and a.identityType = 'ACCOUNT'
          and a.identityId = :accountId
          and a.roleCode = :roleCode
        """)
    int deleteDirectAssignment(@Param("orgId") UUID orgId,
                               @Param("spaceId") UUID spaceId,
                               @Param("accountId") UUID accountId,
                               @Param("roleCode") String roleCode);

    @Query("""
        select count(distinct a.identityId) from RoleAssignmentEntity a
        where a.orgId = :orgId
          and a.identityType = 'ACCOUNT'
          and a.roleCode = :roleCode
          and (a.spaceId = :spaceId or a.spaceId is null)
        """)
    long countDistinctIdentitiesHoldingRole(@Param("orgId") UUID orgId,
                                            @Param("spaceId") UUID spaceId,
                                            @Param("roleCode") String roleCode);
}
