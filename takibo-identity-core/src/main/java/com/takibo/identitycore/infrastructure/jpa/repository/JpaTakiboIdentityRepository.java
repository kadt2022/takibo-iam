package com.takibo.identitycore.infrastructure.jpa.repository;

import com.takibo.identitycore.infrastructure.entity.TakiboIdentityEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface JpaTakiboIdentityRepository extends JpaRepository<TakiboIdentityEntity, UUID> {

    @Query("select t from TakiboIdentityEntity t where t.orgId = :orgId and t.accountId = :accountId")
    Optional<TakiboIdentityEntity> findByOrgIdAndAccountId(@Param("orgId") UUID orgId, 
                                                            @Param("accountId") UUID accountId);

    @Query("select count(t) > 0 from TakiboIdentityEntity t where t.orgId = :orgId and t.accountId = :accountId")
    boolean existsByOrgIdAndAccountId(@Param("orgId") UUID orgId, 
                                      @Param("accountId") UUID accountId);
}