package com.takibo.identitycore.infrastructure.jpa.repository;

import com.takibo.identitycore.application.identity.readmodel.UserReadModel;
import com.takibo.identitycore.domain.status.UserStatus;
import com.takibo.identitycore.domain.type.UserType;
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

    String READ_MODEL_SELECT = """
            select new com.takibo.identitycore.application.identity.readmodel.UserReadModel(
                u.id, u.spaceId, u.accountId, a.email, u.username, u.firstName, u.lastName,
                u.status, u.type, u.mfaEnabled, u.passwordExpired, u.lastLoginAt,
                u.createdAt, u.updatedAt, u.version)
            from UserEntity u join u.account a
            """;

    @Query(value = READ_MODEL_SELECT + """
            where u.spaceId = :spaceId
              and (:status is null or u.status = :status)
              and (:type is null or u.type = :type)
              and (:q is null
                   or lower(u.username) like lower(concat('%', :q, '%'))
                   or lower(a.email) like lower(concat('%', :q, '%'))
                   or lower(u.firstName) like lower(concat('%', :q, '%'))
                   or lower(u.lastName) like lower(concat('%', :q, '%')))
            """,
            countQuery = """
            select count(u)
              from UserEntity u join u.account a
             where u.spaceId = :spaceId
               and (:status is null or u.status = :status)
               and (:type is null or u.type = :type)
               and (:q is null
                    or lower(u.username) like lower(concat('%', :q, '%'))
                    or lower(a.email) like lower(concat('%', :q, '%'))
                    or lower(u.firstName) like lower(concat('%', :q, '%'))
                    or lower(u.lastName) like lower(concat('%', :q, '%')))
            """)
    Page<UserReadModel> findReadModelsBySpace(@Param("spaceId") UUID spaceId,
                                              @Param("status") UserStatus status,
                                              @Param("type") UserType type,
                                              @Param("q") String q,
                                              Pageable pageable);

    @Query(READ_MODEL_SELECT + " where u.spaceId = :spaceId and u.id = :userId")
    Optional<UserReadModel> findReadModelBySpaceAndId(@Param("spaceId") UUID spaceId,
                                                      @Param("userId") UUID userId);


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
