package com.takibo.identitycore.infrastructure.jpa.repository;

import com.takibo.identitycore.infrastructure.entity.AccountEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;


import java.util.Optional;
import java.util.UUID;

@Repository
public interface JpaAccountRepository extends JpaRepository<AccountEntity, UUID> {
    Optional<AccountEntity> findByEmailIgnoreCase(String email);

    boolean existsByEmailIgnoreCase(String email);

    Optional<AccountEntity> findByOrgIdAndEmailIgnoreCase(UUID orgId, String email);

  //  Optional<AccountEntity> findByOrgIdAndEmailIgnoreCase(UUID orgId, String email);
}