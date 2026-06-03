package com.takibo.identitycore.infrastructure.jpa.repository;

import com.takibo.identitycore.infrastructure.entity.GroupMemberEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Set;
import java.util.UUID;

public interface JpaGroupMemberRepository extends JpaRepository<GroupMemberEntity, UUID> {

    boolean existsByOrgIdAndSpaceIdAndUserIdAndGroupId(UUID orgId, UUID spaceId, UUID userId, UUID groupId);

    @Query("""
           select gm.groupId
           from GroupMemberEntity gm
           where gm.orgId   = :orgId
             and gm.spaceId = :spaceId
             and gm.userId  = :userId
             and gm.groupId in :groupIds
           """)
    Set<UUID> findExistingGroupIds(@Param("orgId")    UUID orgId,
                                   @Param("spaceId")  UUID spaceId,
                                   @Param("userId")   UUID userId,
                                   @Param("groupIds") Set<UUID> groupIds);
}
