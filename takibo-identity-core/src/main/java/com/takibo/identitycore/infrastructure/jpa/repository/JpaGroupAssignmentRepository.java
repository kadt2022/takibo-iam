package com.takibo.identitycore.infrastructure.jpa.repository;

import com.takibo.identitycore.domain.model.IdentityType;
import com.takibo.identitycore.infrastructure.entity.GroupAssignmentEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface JpaGroupAssignmentRepository extends JpaRepository<GroupAssignmentEntity, UUID> {

    List<GroupAssignmentEntity> findByOrgIdAndIdentityTypeAndIdentityId(
            UUID orgId, IdentityType identityType, UUID identityId);

    List<GroupAssignmentEntity> findByOrgIdAndSpaceIdAndIdentityTypeAndIdentityId(
            UUID orgId, UUID spaceId, IdentityType identityType, UUID identityId);

    /** Memberships par code (TECHNICAL/GOVERNANCE) d'un account, visibles sur le space. */
    @Query("""
        select a from GroupAssignmentEntity a
        where a.orgId = :orgId
          and a.identityType = 'ACCOUNT'
          and a.identityId = :accountId
          and a.groupCode is not null
          and (a.spaceId = :spaceId or a.spaceId is null)
        """)
    List<GroupAssignmentEntity> findDirectByOrgAndSpaceAndAccount(@Param("orgId") UUID orgId,
                                                                  @Param("spaceId") UUID spaceId,
                                                                  @Param("accountId") UUID accountId);

    @Query("""
        select (count(a) > 0) from GroupAssignmentEntity a
        where a.orgId = :orgId
          and a.identityType = 'ACCOUNT'
          and a.identityId = :accountId
          and a.groupCode = :groupCode
          and (a.spaceId = :spaceId or a.spaceId is null)
        """)
    boolean existsDirectMembership(@Param("orgId") UUID orgId,
                                   @Param("spaceId") UUID spaceId,
                                   @Param("accountId") UUID accountId,
                                   @Param("groupCode") String groupCode);

    @Modifying
    @Query("""
        delete from GroupAssignmentEntity a
        where a.orgId = :orgId
          and a.spaceId = :spaceId
          and a.identityType = 'ACCOUNT'
          and a.identityId = :accountId
          and a.groupCode = :groupCode
        """)
    int deleteDirectMembership(@Param("orgId") UUID orgId,
                               @Param("spaceId") UUID spaceId,
                               @Param("accountId") UUID accountId,
                               @Param("groupCode") String groupCode);

    @Query("""
        select count(distinct a.identityId) from GroupAssignmentEntity a
        where a.orgId = :orgId
          and a.identityType = 'ACCOUNT'
          and a.groupCode = :groupCode
          and (a.spaceId = :spaceId or a.spaceId is null)
        """)
    long countDistinctIdentitiesInGroup(@Param("orgId") UUID orgId,
                                        @Param("spaceId") UUID spaceId,
                                        @Param("groupCode") String groupCode);

    /** Memberships org-level uniquement (space_id IS NULL) — pouvoir de portée ORGANIZATION. */
    @Query("""
        select a from GroupAssignmentEntity a
        where a.orgId = :orgId
          and a.identityType = 'ACCOUNT'
          and a.identityId = :accountId
          and a.groupCode is not null
          and a.spaceId is null
        """)
    List<GroupAssignmentEntity> findOrgLevelByOrgAndAccount(@Param("orgId") UUID orgId,
                                                            @Param("accountId") UUID accountId);

    @Modifying
    @Query("""
        delete from GroupAssignmentEntity a
        where a.orgId = :orgId
          and a.spaceId is null
          and a.identityType = 'ACCOUNT'
          and a.identityId = :accountId
          and a.groupCode = :groupCode
        """)
    int deleteOrgLevelMembership(@Param("orgId") UUID orgId,
                                 @Param("accountId") UUID accountId,
                                 @Param("groupCode") String groupCode);
}
