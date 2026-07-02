package com.takibo.identitycore.infrastructure.jpa.repository;

import com.takibo.identitycore.infrastructure.entity.UserEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface JpaUserRepository extends JpaRepository<UserEntity, UUID>, JpaSpecificationExecutor<UserEntity> {


    boolean existsBySpaceIdAndUsernameIgnoreCase(UUID spaceId, String username);

    boolean existsBySpaceIdAndUsernameIgnoreCaseAndIdNot(UUID spaceId, String username, UUID excludeUserId);

    Optional<UserEntity> findBySpaceIdAndUsername(UUID spaceId, String username);

    @Query("""
            select u
              from UserEntity u
              join u.account a
             where u.spaceId = :spaceId
               and lower(a.email) = lower(:email)
            """)
    Optional<UserEntity> findBySpaceIdAndEmailIgnoreCase(@Param("spaceId") UUID spaceId, @Param("email") String email);

    @Query("""
            select (count(u) > 0)
              from UserEntity u
              join u.account a
             where u.spaceId = :spaceId
               and lower(a.email) = lower(:email)
               and u.id <> :excludeId
            """)
    boolean existsBySpaceIdAndEmailIgnoreCaseAndIdNot(@Param("spaceId") UUID spaceId,
                                                      @Param("email") String email,
                                                      @Param("excludeId") UUID excludeId);

    boolean existsBySpaceIdAndAccountId(UUID spaceId, UUID accountId);

    Optional<UserEntity> findBySpaceIdAndAccountId(UUID spaceId, UUID accountId);

    Page<UserEntity> findAllBySpaceId(UUID spaceId, Pageable pageable);

    @Modifying
    @Query(value = "DELETE FROM user_group_memberships WHERE user_id = :userId", nativeQuery = true)
    void deleteUserGroups(@Param("userId") UUID userId);

    @Modifying
    @Query(value = "DELETE FROM user_roles WHERE user_id = :userId", nativeQuery = true)
    void deleteUserRoles(@Param("userId") UUID userId);
}
